package com.acove.blog.auth.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.acove.blog.common.exception.BusinessException;

class ProfilePageHtmlPackageValidatorTest {

    private ProfilePageHtmlPackageValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ProfilePageHtmlPackageValidator();
    }

    @Test
    void validate_acceptsOnePageAndLocalPresentationAssets() throws IOException {
        MockMultipartFile archive = archive(
                entry("index.html", "<!doctype html><html><head><link rel=\"stylesheet\" href=\"css/site.css\"></head>"
                        + "<body><img src=\"images/me.png\"><a href=\"https://example.com\">link</a></body></html>"),
                entry("css/site.css", "body { background-image: url('../images/me.png'); }"),
                entry("images/me.png", "image bytes"));

        ProfilePageHtmlPackageValidator.ValidatedPackage result = validator.validate(archive);

        assertEquals(2, result.assets().size());
        assertEquals("<!doctype html><html><head><link rel=\"stylesheet\" href=\"css/site.css\"></head>"
                        + "<body><img src=\"images/me.png\"><a href=\"https://example.com\">link</a></body></html>",
                result.htmlDocument());
    }

    @Test
    void validate_acceptsAitorProfileWithInlineSvg() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/profile-page/aitor-profile.zip")) {
            MockMultipartFile archive = new MockMultipartFile("file", "aitor-profile.zip", "application/zip", input);
            ProfilePageHtmlPackageValidator.ValidatedPackage result = validator.validate(archive);

            assertEquals(0, result.assets().size());
            try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                    archive.getInputStream(), StandardCharsets.UTF_8)) {
                assertEquals("index.html", zip.getNextEntry().getName());
                assertEquals(new String(zip.readAllBytes(), StandardCharsets.UTF_8), result.htmlDocument());
            }
        }
    }

    @Test
    void validate_acceptsStaticSvgDefinitionsTextAndLocalReferences() throws IOException {
        String html = "<html><body><svg xmlns=\"http://www.w3.org/2000/svg\" "
                + "xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"0 0 24 24\">"
                + "<defs><linearGradient id=\"paint\"><stop offset=\"0\" stop-color=\"red\"/>"
                + "</linearGradient><symbol id=\"icon\"><path d=\"M0 0h24v24\"/></symbol></defs>"
                + "<use xlink:href=\"#icon\" fill=\"url(#paint)\"/>"
                + "<svg><text><tspan>你好</tspan></text></svg></svg><svg/>"
                + "<a href=\"https://example.com\">link</a></body></html>";

        assertEquals(html, validator.validate(archive(entry("index.html", html))).htmlDocument());
    }

    @Test
    void validate_rejectsSvgScriptsEventsEmbeddedContentAndAnimation() throws IOException {
        for (String content : new String[] {
                "<script>alert(1)</script>",
                "<foreignObject><iframe src=\"https://example.com\"/></foreignObject>",
                "<path onload=\"alert(1)\"/>",
                "<animate attributeName=\"href\" to=\"https://example.com\"/>",
                "<set attributeName=\"onload\" to=\"alert(1)\"/>" }) {
            assertRejected(archive(entry("index.html", "<svg>" + content + "</svg>")));
        }
    }

    @Test
    void validate_rejectsSvgExternalAndEncodedResourceReferences() throws IOException {
        for (String content : new String[] {
                "<use href=\"https://example.com/icons.svg#icon\"/>",
                "<use href=\"icons.svg#icon\"/>",
                "<use href=\"&#106;avascript:alert(1)\"/>",
                "<use xmlns:xlink=\"http://www.w3.org/1999/xlink\" xlink:href=\"//example.com/icon\"/>",
                "<image href=\"https://example.com/image.png\"/>",
                "<path fill=\"url(https://example.com/paint.svg#paint)\"/>",
                "<path style=\"fill:url(&#104;ttps://example.com/paint.svg#paint)\"/>",
                "<style>@import 'https://example.com/style.css';</style>",
                "<g xml:base=\"https://example.com\"><use href=\"#icon\"/></g>",
                "<path xmlns=\"http://www.w3.org/1999/xhtml\"/>" }) {
            assertRejected(archive(entry("index.html", "<svg>" + content + "</svg>")));
        }
    }

    @Test
    void validate_rejectsMalformedSvgAndXmlDirectives() throws IOException {
        for (String html : new String[] {
                "<svg><path></svg>", "<svg><path/>", "</svg>",
                "<svg><!DOCTYPE svg [<!ENTITY payload SYSTEM 'file:///etc/passwd'>]>"
                        + "<text>&payload;</text></svg>",
                "<svg><?xml-stylesheet href=\"https://example.com/style.css\"?></svg>",
                "<svg>" + "<g>".repeat(101) + "</g>".repeat(101) + "</svg>" }) {
            assertRejected(archive(entry("index.html", html)));
        }
    }

    @Test
    void validate_rejectsMissingRootIndex() throws IOException {
        assertRejected(archive(entry("pages/index.html", "<html><body>hello</body></html>")));
    }

    @Test
    void validate_rejectsMultipleHtmlDocuments() throws IOException {
        assertRejected(archive(
                entry("index.html", "<html><body>hello</body></html>"),
                entry("other.html", "<html><body>other</body></html>")));
    }

    @Test
    void validate_rejectsActiveHtmlAndExternalResources() throws IOException {
        assertRejected(archive(entry("index.html", "<html><script>alert(1)</script></html>")));
        assertRejected(archive(entry("index.html", "<html><body><img src=\"https://evil.example/x.png\"></body></html>")));
        assertRejected(archive(entry("index.html", "<html><body><iframe src=\"https://evil.example\"></iframe></body></html>")));
        assertRejected(archive(entry("index.html", "<html><body><form action=\"/\"></form></body></html>")));
    }

    @Test
    void validate_rejectsPathTraversalAndTooManyEntries() throws IOException {
        assertRejected(archive(
                entry("index.html", "<html><body>hello</body></html>"),
                entry("assets/../escape.css", "body {}")));

        Entry[] entries = new Entry[ProfilePageHtmlPackageValidator.MAX_FILES + 1];
        entries[0] = entry("index.html", "<html><body>hello</body></html>");
        for (int i = 1; i < entries.length; i++) {
            entries[i] = entry("asset" + i + ".css", "body { color: #000; }");
        }
        assertRejected(archive(entries));
    }

    @Test
    void validate_rejectsArchivesOverCompressedAndExpandedLimits() throws IOException {
        byte[] oversizedArchive = new byte[(int) ProfilePageHtmlPackageValidator.MAX_ARCHIVE_SIZE + 1];
        assertRejected(new MockMultipartFile("file", "profile.zip", "application/zip", oversizedArchive));

        String expandedHtml = " ".repeat((int) ProfilePageHtmlPackageValidator.MAX_EXPANDED_SIZE + 1);
        assertRejected(archive(entry("index.html", expandedHtml)));
        assertRejected(archive(
                entry("payload/", expandedHtml),
                entry("index.html", "<html><body>hello</body></html>")));
    }

    private void assertRejected(MockMultipartFile archive) {
        assertThrows(BusinessException.class, () -> validator.validate(archive));
    }

    private MockMultipartFile archive(Entry... entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Entry item : entries) {
                zip.putNextEntry(new ZipEntry(item.path()));
                zip.write(item.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "profile.zip", "application/zip", bytes.toByteArray());
    }

    private static Entry entry(String path, String content) {
        return new Entry(path, content);
    }

    private record Entry(String path, String content) {
    }
}
