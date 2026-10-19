#!/usr/bin/env python3
"""Generate the checked-in Maple Mono CN GB2312 WOFF2 webfont.

Requires Python 3.11+ and the pinned dependencies in
font-tools-requirements.txt. For example:

    python3 -m venv /tmp/acove-font-tools
    /tmp/acove-font-tools/bin/pip install -r font-tools-requirements.txt
    /tmp/acove-font-tools/bin/python scripts/subset_maple_font.py
"""

from __future__ import annotations

import subprocess
import sys
import tempfile
import unicodedata
from pathlib import Path

from fontTools.ttLib import TTFont


FRONTEND_DIR = Path(__file__).resolve().parents[1]
SOURCE_FONT = FRONTEND_DIR / "font-source" / "MapleMono-CN-Regular.ttf"
OUTPUT_FONT = (
    FRONTEND_DIR
    / "src"
    / "assets"
    / "fonts"
    / "MapleMono-CN-Regular-GB2312.woff2"
)
MAX_OUTPUT_BYTES = 5_000_000
SOURCE_EXTENSIONS = {".css", ".html", ".js", ".jsx", ".json", ".ts", ".tsx", ".vue"}


def add_unicode_range(characters: set[str], start: int, end: int) -> None:
    characters.update(chr(codepoint) for codepoint in range(start, end + 1))


def collect_characters() -> set[str]:
    characters: set[str] = set()

    # GB2312 includes common simplified Chinese characters and Chinese punctuation.
    for lead in range(0xA1, 0xF8):
        for trail in range(0xA1, 0xFF):
            try:
                characters.add(bytes((lead, trail)).decode("gb2312"))
            except UnicodeDecodeError:
                pass

    # Keep standard Latin, punctuation, currency, letter-like symbols and fullwidth forms.
    for start, end in (
        (0x0020, 0x007E),
        (0x00A0, 0x024F),
        (0x2000, 0x206F),
        (0x20A0, 0x20CF),
        (0x2100, 0x214F),
        (0x3000, 0x303F),
        (0xFF00, 0xFFEF),
    ):
        add_unicode_range(characters, start, end)

    # Include every character used by the current UI source, including project-specific labels.
    source_dir = FRONTEND_DIR / "src"
    for path in source_dir.rglob("*"):
        if path.is_file() and path.suffix.lower() in SOURCE_EXTENSIONS:
            characters.update(path.read_text(encoding="utf-8"))

    # Drop controls and formatting-only codepoints; retain normal and ideographic spaces.
    return {
        character
        for character in characters
        if unicodedata.category(character)[0] != "C"
        or character in {"\u0020", "\u00a0", "\u3000"}
    }


def main() -> int:
    if not SOURCE_FONT.is_file():
        raise SystemExit(f"Source font not found: {SOURCE_FONT}")

    characters = collect_characters()
    source_font = TTFont(SOURCE_FONT)
    supported_codepoints = set(source_font.getBestCmap())
    requested_codepoints = {ord(character) for character in characters}
    expected_codepoints = requested_codepoints & supported_codepoints
    source_font.close()

    OUTPUT_FONT.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        prefix=".maple-mono-cn-", suffix=".woff2", dir=OUTPUT_FONT.parent, delete=False
    ) as temp_output:
        output_file = Path(temp_output.name)

    try:
        with tempfile.TemporaryDirectory(prefix="acove-maple-font-") as temp_dir:
            temp_dir_path = Path(temp_dir)
            text_file = temp_dir_path / "subset-characters.txt"
            text_file.write_text("".join(sorted(characters)), encoding="utf-8")

            subprocess.run(
                [
                    sys.executable,
                    "-m",
                    "fontTools.subset",
                    str(SOURCE_FONT),
                    f"--text-file={text_file}",
                    f"--output-file={output_file}",
                    "--flavor=woff2",
                    "--layout-features=*",
                    "--notdef-glyph",
                    "--notdef-outline",
                    "--recommended-glyphs",
                ],
                check=True,
            )

            subset_font = TTFont(output_file)
            output_codepoints = set(subset_font.getBestCmap())
            subset_font.close()
            missing_codepoints = expected_codepoints - output_codepoints
            if missing_codepoints:
                sample = ", ".join(
                    f"U+{codepoint:04X}" for codepoint in sorted(missing_codepoints)[:8]
                )
                raise SystemExit(
                    f"Generated subset lost {len(missing_codepoints)} source glyphs: {sample}"
                )

            output_size = output_file.stat().st_size
            if output_size > MAX_OUTPUT_BYTES:
                raise SystemExit(
                    f"Generated font is {output_size:,} bytes; "
                    f"target is below {MAX_OUTPUT_BYTES:,} bytes"
                )

        output_file.replace(OUTPUT_FONT)
    finally:
        output_file.unlink(missing_ok=True)

    original_size = SOURCE_FONT.stat().st_size
    output_size = OUTPUT_FONT.stat().st_size
    reduction = (1 - output_size / original_size) * 100
    print(
        f"Generated {OUTPUT_FONT.relative_to(FRONTEND_DIR)}: "
        f"{output_size:,} bytes from {len(expected_codepoints):,} source glyphs "
        f"({reduction:.1f}% smaller than the {original_size:,}-byte TTF)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
