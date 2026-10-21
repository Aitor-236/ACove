package com.acove.blog.common.utils;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import net.coobird.thumbnailator.Thumbnails;

/** Creates display-sized WebP files beside uploaded article and gallery originals. */
public final class ImageThumbnailService {

    public static final int SMALL_WIDTH = 480;
    public static final int LARGE_WIDTH = 1600;
    private static final float OUTPUT_QUALITY = 0.8f;
    private static final int[] WIDTHS = { SMALL_WIDTH, LARGE_WIDTH };
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");

    private ImageThumbnailService() {
    }

    /**
     * Generate any missing variants. Existing variants and the original are never overwritten.
     * GIF input is read as its first frame; the original remains available for animation.
     *
     * @return number of variants created
     */
    public static int generateMissingVariants(Path original) throws IOException {
        if (!Files.isRegularFile(original) || !isSupportedOriginal(original)) {
            return 0;
        }

        int generated = 0;
        for (int width : WIDTHS) {
            Path variant = variantPath(original, width);
            if (Files.exists(variant)) {
                continue;
            }
            if (createVariant(original, variant, width)) {
                generated++;
            }
        }
        return generated;
    }

    public static Path variantPath(Path original, int width) {
        if (width != SMALL_WIDTH && width != LARGE_WIDTH) {
            throw new IllegalArgumentException("Unsupported thumbnail width: " + width);
        }
        String filename = original.getFileName().toString();
        int extensionStart = filename.lastIndexOf('.');
        String basename = extensionStart > 0 ? filename.substring(0, extensionStart) : filename;
        return original.resolveSibling(basename + ".w" + width + ".webp");
    }

    public static void deleteVariants(Path original) throws IOException {
        for (int width : WIDTHS) {
            Files.deleteIfExists(variantPath(original, width));
        }
    }

    /** Scan the existing article and gallery upload directories without starting Spring. */
    public static BackfillSummary backfill(Path uploadRoot) {
        int originalsVisited = 0;
        int variantsGenerated = 0;
        int failures = 0;

        for (String directoryName : new String[] { "article", "gallery" }) {
            Path directory = uploadRoot.resolve(directoryName);
            if (!Files.isDirectory(directory)) {
                continue;
            }

            try (Stream<Path> files = Files.walk(directory)) {
                Iterator<Path> iterator = files.filter(Files::isRegularFile)
                        .filter(ImageThumbnailService::isSupportedOriginal)
                        .iterator();
                while (iterator.hasNext()) {
                    Path original = iterator.next();
                    originalsVisited++;
                    try {
                        variantsGenerated += generateMissingVariants(original);
                    } catch (IOException | RuntimeException exception) {
                        failures++;
                        System.err.printf("Failed to generate thumbnails for %s: %s%n",
                                original, exception.getMessage());
                    }
                }
            } catch (IOException | RuntimeException exception) {
                failures++;
                System.err.printf("Failed to scan %s: %s%n", directory, exception.getMessage());
            }
        }

        return new BackfillSummary(originalsVisited, variantsGenerated, failures);
    }

    public static boolean isSupportedOriginal(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".w" + SMALL_WIDTH + ".webp")
                || filename.endsWith(".w" + LARGE_WIDTH + ".webp")) {
            return false;
        }
        int extensionStart = filename.lastIndexOf('.');
        return extensionStart > 0 && IMAGE_EXTENSIONS.contains(filename.substring(extensionStart + 1));
    }

    private static boolean createVariant(Path original, Path target, int width) throws IOException {
        double scale = scaleFor(original, width);
        Path temporary = Files.createTempFile(target.getParent(), ".acove-thumb-", ".tmp");
        try {
            Thumbnails.of(original.toFile())
                    .scale(scale)
                    .outputFormat("webp")
                    .outputQuality(OUTPUT_QUALITY)
                    .useExifOrientation(true)
                    .toFile(temporary.toFile());

            try {
                Files.move(temporary, target);
                return true;
            } catch (FileAlreadyExistsException ignored) {
                // Another upload/backfill process created the same immutable derivative.
                return false;
            }
        } catch (RuntimeException exception) {
            throw new IOException("Image conversion failed", exception);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Keep the requested long edge as a maximum; small originals are never enlarged. */
    private static double scaleFor(Path original, int maxEdge) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(original.toFile())) {
            if (input == null) {
                throw new IOException("Unable to read image input");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image format");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int originalMaxEdge = Math.max(reader.getWidth(0), reader.getHeight(0));
                if (originalMaxEdge <= 0) {
                    throw new IOException("Invalid image dimensions");
                }
                return Math.min(1d, (double) maxEdge / originalMaxEdge);
            } finally {
                reader.dispose();
            }
        }
    }

    public record BackfillSummary(int originalsVisited, int variantsGenerated, int failures) {
    }
}
