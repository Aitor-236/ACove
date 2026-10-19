package com.acove.blog.auth.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.common.exception.BusinessException;

/** Validates and unpacks one static HTML page plus local presentation assets. */
@Component
public class ProfilePageHtmlPackageValidator {

    public static final long MAX_ARCHIVE_SIZE = 5L * 1024 * 1024;
    public static final long MAX_EXPANDED_SIZE = 20L * 1024 * 1024;
    public static final int MAX_FILES = 100;
    private static final int MAX_TOTAL_ENTRIES = 1_000;

    private static final Set<String> ASSET_EXTENSIONS = Set.of(
            "css", "png", "jpg", "jpeg", "webp", "gif", "woff", "woff2", "ttf", "otf");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final Pattern TAG_PATTERN = Pattern.compile("(?is)<\\s*([a-z][a-z0-9:-]*)\\b([^>]*)>");
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "(?is)([a-z_:][a-z0-9_:.-]*)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+)))?");
    private static final Pattern CSS_URL_PATTERN = Pattern.compile(
            "(?is)url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)");
    private static final Pattern CSS_IMPORT_PATTERN = Pattern.compile(
            "(?is)@import\\s+(?:url\\(\\s*)?['\"]?([^'\")\\s;]+)['\"]?\\s*\\)?");
    private static final Pattern STYLE_BLOCK_PATTERN = Pattern.compile(
            "(?is)<style\\b[^>]*>(.*?)</style\\s*>");
    private static final Pattern SVG_TAG_PATTERN = Pattern.compile("(?is)<(/?)svg\\b[^>]*>");
    private static final Set<String> SVG_ELEMENTS = Set.of(
            "svg", "g", "defs", "symbol", "use", "path", "rect", "circle", "ellipse", "line",
            "polyline", "polygon", "text", "tspan", "textpath", "title", "desc", "style",
            "lineargradient", "radialgradient", "stop", "clippath", "mask", "pattern", "marker");

    public ValidatedPackage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的 ZIP 资源包");
        }
        if (file.getSize() > MAX_ARCHIVE_SIZE) {
            throw new BusinessException("HTML资源包不能超过 5MB");
        }

        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName) || !originalName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new BusinessException("请上传 ZIP 格式的 HTML 资源包");
        }

        Map<String, byte[]> assets = new LinkedHashMap<>();
        String htmlDocument = null;
        long expandedSize = 0L;
        int files = 0;
        int entries = 0;

        try (InputStream input = file.getInputStream();
                ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries += 1;
                if (entries > MAX_TOTAL_ENTRIES) {
                    throw new BusinessException("HTML资源包中的文件和目录条目过多");
                }

                String path = normalizeEntryName(entry.getName(), entry.isDirectory());
                byte[] content = readEntry(zip, MAX_EXPANDED_SIZE - expandedSize);
                expandedSize += content.length;
                if (expandedSize > MAX_EXPANDED_SIZE) {
                    throw new BusinessException("HTML资源包解压后不能超过 20MB");
                }

                if (!entry.isDirectory()) {
                    files += 1;
                    if (files > MAX_FILES) {
                        throw new BusinessException("HTML资源包最多允许 100 个文件");
                    }

                    if (path.equalsIgnoreCase("index.html")) {
                        if (htmlDocument != null) {
                            throw new BusinessException("资源包只能包含一个根目录 index.html");
                        }
                        htmlDocument = decodeUtf8(content);
                    } else {
                        String extension = extensionOf(path);
                        if (extension.equals("html") || extension.equals("htm")) {
                            throw new BusinessException("资源包不能包含其他 HTML 页面");
                        }
                        if (!ASSET_EXTENSIONS.contains(extension)) {
                            throw new BusinessException("资源包只支持 CSS、图片和字体文件");
                        }
                        if (assets.putIfAbsent(path, content) != null) {
                            throw new BusinessException("资源包里有重复文件路径");
                        }
                    }
                }
                zip.closeEntry();
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            throw new BusinessException("ZIP资源包无法读取，请检查文件是否完整");
        }

        if (htmlDocument == null) {
            throw new BusinessException("资源包根目录必须包含 index.html");
        }
        if (!StringUtils.hasText(htmlDocument)) {
            throw new BusinessException("index.html 不能为空");
        }

        validateHtml(htmlDocument, assets);
        for (Map.Entry<String, byte[]> asset : assets.entrySet()) {
            if (asset.getKey().toLowerCase(Locale.ROOT).endsWith(".css")) {
                validateCss(new String(asset.getValue(), StandardCharsets.UTF_8), asset.getKey(), assets);
            }
        }

        return new ValidatedPackage(htmlDocument, assets);
    }

    private String normalizeEntryName(String entryName, boolean directory) {
        if (!StringUtils.hasText(entryName)
                || entryName.indexOf('\\') >= 0
                || entryName.indexOf('\0') >= 0
                || entryName.startsWith("/")
                || entryName.matches("^[A-Za-z]:.*")) {
            throw new BusinessException("资源包包含不安全的文件路径");
        }

        for (String segment : entryName.split("/")) {
            if (segment.equals("..")) {
                throw new BusinessException("资源包包含不安全的文件路径");
            }
        }

        Path normalized;
        try {
            normalized = Paths.get(entryName).normalize();
        } catch (RuntimeException ex) {
            throw new BusinessException("资源包包含不安全的文件路径");
        }
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            throw new BusinessException("资源包包含不安全的文件路径");
        }

        if (normalized.toString().isBlank() || normalized.toString().equals(".")) {
            if (directory) return ".";
            throw new BusinessException("资源包包含不安全的文件路径");
        }

        String path = normalized.toString().replace('\\', '/');
        if (!path.matches("[A-Za-z0-9._/-]+")) {
            throw new BusinessException("资源包文件名只支持英文、数字、点、下划线和连字符");
        }
        return path;
    }

    private byte[] readEntry(ZipInputStream zip, long remainingLimit) throws IOException {
        if (remainingLimit < 0) {
            throw new BusinessException("HTML资源包解压后不能超过 20MB");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            if (output.size() + (long) read > remainingLimit) {
                throw new BusinessException("HTML资源包解压后不能超过 20MB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new BusinessException("index.html 必须使用 UTF-8 编码");
        }
    }

    private void validateHtml(String html, Map<String, byte[]> assets) {
        String lowerHtml = html.toLowerCase(Locale.ROOT);
        if (lowerHtml.contains("javascript:") || lowerHtml.contains("vbscript:")) {
            throw new BusinessException("HTML页面不能包含脚本链接");
        }

        // Validate SVG as XML (including decoded attributes), then scan the remaining HTML.
        // Keeping SVG out of the HTML href rules permits local <use href="#icon"> references.
        html = validateAndRemoveInlineSvg(html, assets);

        Matcher tagMatcher = TAG_PATTERN.matcher(html);
        while (tagMatcher.find()) {
            String tag = tagMatcher.group(1).toLowerCase(Locale.ROOT);
            Map<String, String> attributes = parseAttributes(tagMatcher.group(2));
            if (Set.of("script", "form", "iframe", "frame", "frameset", "object", "embed", "applet",
                    "svg", "video", "audio", "source", "track", "base", "input", "button", "select",
                    "textarea", "canvas", "portal").contains(tag)) {
                throw new BusinessException("HTML资源包只支持静态展示内容，不能包含脚本、表单或嵌套页面");
            }
            if (tag.equals("meta") && attributes.containsKey("http-equiv")) {
                throw new BusinessException("HTML页面不能包含自动跳转配置");
            }
            if (attributes.keySet().stream().anyMatch(name -> name.startsWith("on"))) {
                throw new BusinessException("HTML页面不能包含事件脚本");
            }
            if (attributes.containsKey("srcset") || attributes.containsKey("poster")) {
                throw new BusinessException("HTML页面只支持本地图片资源");
            }

            if (tag.equals("img")) {
                String src = attributes.get("src");
                if (src == null || !isImageReference(src, "index.html", assets)) {
                    throw new BusinessException("图片必须引用资源包内的 PNG、JPG、WebP 或 GIF 文件");
                }
            } else if (tag.equals("link")) {
                if (!"stylesheet".equalsIgnoreCase(attributes.get("rel"))) {
                    throw new BusinessException("HTML页面只能通过 link 引入本地 CSS 样式");
                }
                String href = attributes.get("href");
                if (href == null || !isAssetReference(href, "index.html", assets, "css")) {
                    throw new BusinessException("CSS 必须引用资源包内的文件");
                }
            } else if (tag.equals("a")) {
                String href = attributes.get("href");
                if (href != null && !isAllowedLink(href)) {
                    throw new BusinessException("页面链接只支持 HTTPS、mailto 和页内锚点");
                }
            } else if (attributes.containsKey("src") || attributes.containsKey("href")) {
                throw new BusinessException("HTML页面只支持图片、样式表和普通链接");
            }

            String style = attributes.get("style");
            if (style != null) {
                validateCss(style, "index.html", assets);
            }
        }

        Matcher styles = STYLE_BLOCK_PATTERN.matcher(html);
        while (styles.find()) {
            validateCss(styles.group(1), "index.html", assets);
        }
    }

    private String validateAndRemoveInlineSvg(String html, Map<String, byte[]> assets) {
        Matcher matcher = SVG_TAG_PATTERN.matcher(html);
        StringBuilder remainingHtml = new StringBuilder();
        int depth = 0;
        int start = 0;
        int copiedUntil = 0;
        while (matcher.find()) {
            boolean closing = !matcher.group(1).isEmpty();
            if (closing) {
                if (depth == 0) {
                    throw new BusinessException("内联 SVG 标签不完整");
                }
                depth -= 1;
            } else {
                if (depth == 0) start = matcher.start();
                if (!matcher.group().endsWith("/>")) depth += 1;
            }
            if (depth == 0) {
                validateInlineSvg(html.substring(start, matcher.end()), assets);
                remainingHtml.append(html, copiedUntil, start).append(' ');
                copiedUntil = matcher.end();
            }
        }
        if (depth != 0) {
            throw new BusinessException("内联 SVG 标签不完整");
        }
        return remainingHtml.append(html, copiedUntil, html.length()).toString();
    }

    private void validateInlineSvg(String svg, Map<String, byte[]> assets) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute("jdk.xml.maxElementDepth", "100");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            Element root = builder.parse(new InputSource(new StringReader(svg))).getDocumentElement();
            validateSvgNode(root, assets);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("内联 SVG 格式无效，请使用完整的静态 SVG 标签");
        }
    }

    private void validateSvgNode(Node node, Map<String, byte[]> assets) {
        if (node instanceof Element element) {
            String tag = element.getTagName().toLowerCase(Locale.ROOT);
            String namespace = element.getNamespaceURI();
            if (!SVG_ELEMENTS.contains(tag)
                    || (namespace != null && !namespace.equals("http://www.w3.org/2000/svg"))) {
                throw new BusinessException("内联 SVG 只支持静态图形和文字，不能包含脚本、动画或嵌入页面");
            }
            var attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                String name = attribute.getNodeName().toLowerCase(Locale.ROOT);
                String value = attribute.getNodeValue().trim();
                if (name.startsWith("on") || name.equals("src") || name.equals("xml:base")) {
                    throw new BusinessException("内联 SVG 不能包含事件脚本或外部资源");
                }
                if (name.equals("href") || name.equals("xlink:href")) {
                    if (!value.startsWith("#") || value.length() == 1) {
                        throw new BusinessException("内联 SVG 只能引用页内图形或文字");
                    }
                } else if (name.contains(":") && !name.equals("xml:space")
                        && !(name.equals("xmlns:xlink") && value.equals("http://www.w3.org/1999/xlink"))) {
                    throw new BusinessException("内联 SVG 包含不支持的命名空间属性");
                }
                validateCss(value, "index.html", assets);
            }
            if (tag.equals("style")) validateCss(element.getTextContent(), "index.html", assets);
        } else if (node.getNodeType() != Node.TEXT_NODE && node.getNodeType() != Node.COMMENT_NODE
                && node.getNodeType() != Node.CDATA_SECTION_NODE) {
            throw new BusinessException("内联 SVG 包含不支持的内容");
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            validateSvgNode(child, assets);
        }
    }

    private Map<String, String> parseAttributes(String source) {
        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE_PATTERN.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = matcher.group(2) != null ? matcher.group(2)
                    : matcher.group(3) != null ? matcher.group(3)
                            : matcher.group(4) != null ? matcher.group(4) : "";
            attributes.put(name, value.trim());
        }
        return attributes;
    }

    private boolean isAllowedLink(String value) {
        String href = value.trim().toLowerCase(Locale.ROOT);
        return href.startsWith("https://") || href.startsWith("mailto:") || href.startsWith("#");
    }

    private boolean isImageReference(String value, String sourcePath, Map<String, byte[]> assets) {
        String extension = extensionOf(stripSuffix(value));
        return IMAGE_EXTENSIONS.contains(extension) && isAssetReference(value, sourcePath, assets, extension);
    }

    private boolean isAssetReference(String value, String sourcePath, Map<String, byte[]> assets, String expectedExtension) {
        String reference = stripSuffix(value.trim());
        if (!StringUtils.hasText(reference)
                || reference.startsWith("/")
                || reference.startsWith("\\")
                || reference.contains("\\")
                || reference.contains(":")
                || reference.contains("%")) {
            return false;
        }

        Path base = Paths.get(sourcePath).getParent();
        Path target;
        try {
            target = (base == null ? Paths.get(reference) : base.resolve(reference)).normalize();
        } catch (RuntimeException ex) {
            return false;
        }
        if (target.isAbsolute() || target.startsWith("..")) {
            return false;
        }

        String normalized = target.toString().replace('\\', '/');
        return assets.containsKey(normalized) && extensionOf(normalized).equals(expectedExtension);
    }

    private void validateCss(String css, String sourcePath, Map<String, byte[]> assets) {
        Matcher importMatcher = CSS_IMPORT_PATTERN.matcher(css);
        while (importMatcher.find()) {
            if (!isAssetReference(importMatcher.group(1), sourcePath, assets, "css")) {
                throw new BusinessException("CSS 只能导入资源包内的样式文件");
            }
        }

        Matcher urlMatcher = CSS_URL_PATTERN.matcher(css);
        while (urlMatcher.find()) {
            String value = urlMatcher.group(1).trim();
            if (value.startsWith("#")) {
                continue;
            }
            String extension = extensionOf(stripSuffix(value));
            if (!ASSET_EXTENSIONS.contains(extension)
                    || !isAssetReference(value, sourcePath, assets, extension)) {
                throw new BusinessException("CSS 只能引用资源包内的图片或字体");
            }
        }
    }

    private String stripSuffix(String value) {
        int query = value.indexOf('?');
        int fragment = value.indexOf('#');
        int end = value.length();
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        return value.substring(0, end);
    }

    private String extensionOf(String path) {
        int separator = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > separator ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    public record ValidatedPackage(String htmlDocument, Map<String, byte[]> assets) {
    }
}
