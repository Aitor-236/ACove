package com.acove.blog.common.utils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private static final String TEMP_PREFIX = ".acove-thumb-";
    private static final float OUTPUT_QUALITY = 0.8f;
    private static final int[] WIDTHS = { SMALL_WIDTH, LARGE_WIDTH };
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");

    private ImageThumbnailService() {
    }

    /** Generate missing or invalid variants; preserve valid variants and original bytes.
     * GIF input is read as its first frame; the original remains available for animation.
     * @return number of variants created or repaired
     */
    public static int generateMissingVariants(Path original) throws IOException {
        int[] counts = new int[2];
        generateVariants(original, counts);
        return counts[0] + counts[1];
    }

    private static void generateVariants(Path original, int[] counts) throws IOException {
        if (!Files.isRegularFile(original) || !isSupportedOriginal(original)) {
            return;
        }
        int originalEdge = originalLongEdge(original);
        for (int width : WIDTHS) {
            int expectedEdge = Math.min(width, originalEdge);
            Path variant = variantPath(original, width);
            if (isValidVariant(variant, expectedEdge)) {
                continue;
            }
            boolean repair = Files.exists(variant);
            createVariant(original, variant, Math.min(1d, (double) width / originalEdge), expectedEdge);
            counts[repair ? 1 : 0]++;
        }
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

    /** Offline maintenance: stop all upload writers before cleaning and scanning these directories. */
    public static BackfillSummary backfill(Path uploadRoot) {
        int originalsVisited = 0;
        int variantsGenerated = 0;
        int failures = 0;
        int variantsRepaired = 0;
        int temporaryFilesCleaned = 0;

        for (String directoryName : new String[] { "article", "gallery" }) {
            Path directory = uploadRoot.resolve(directoryName);
            if (!Files.isDirectory(directory)) {
                continue;
            }

            // Reserved names include the old .tmp.webp leak and derivatives made from it.
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(ImageThumbnailService::isTemporary).toList()) {
                    try {
                        Files.delete(file);
                        temporaryFilesCleaned++;
                    } catch (IOException exception) {
                        failures++;
                        System.err.printf("Failed to clean thumbnail temporary file %s: %s%n",
                                file, exception.getMessage());
                    }
                }
            } catch (IOException | RuntimeException exception) {
                failures++;
                System.err.printf("Failed to clean %s: %s%n", directory, exception.getMessage());
            }

            try (Stream<Path> files = Files.walk(directory)) {
                Iterator<Path> iterator = files.filter(Files::isRegularFile)
                        .filter(ImageThumbnailService::isSupportedOriginal)
                        .iterator();
                while (iterator.hasNext()) {
                    Path original = iterator.next();
                    originalsVisited++;
                    int[] counts = new int[2];
                    try {
                        generateVariants(original, counts);
                    } catch (IOException | RuntimeException exception) {
                        failures++;
                        System.err.printf("Failed to generate thumbnails for %s: %s%n",
                                original, exception.getMessage());
                    } finally {
                        variantsGenerated += counts[0];
                        variantsRepaired += counts[1];
                    }
                }
            } catch (IOException | RuntimeException exception) {
                failures++;
                System.err.printf("Failed to scan %s: %s%n", directory, exception.getMessage());
            }
        }

        return new BackfillSummary(originalsVisited, variantsGenerated, variantsRepaired, temporaryFilesCleaned, failures);
    }

    public static boolean isSupportedOriginal(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (isTemporary(path)) {
            return false;
        }
        if (filename.endsWith(".w" + SMALL_WIDTH + ".webp")
                || filename.endsWith(".w" + LARGE_WIDTH + ".webp")) {
            return false;
        }
        int extensionStart = filename.lastIndexOf('.');
        return extensionStart > 0 && IMAGE_EXTENSIONS.contains(filename.substring(extensionStart + 1));
    }

    private static boolean isTemporary(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(TEMP_PREFIX);
    }

    private static boolean isValidVariant(Path path, int expectedEdge) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) == 0) {
            return false;
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) {
                return false;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                if (!"webp".equalsIgnoreCase(reader.getFormatName())) {
                    return false;
                }
                reader.setInput(input, true, true);
                BufferedImage image = reader.read(0);
                return image != null && Math.max(image.getWidth(), image.getHeight()) == expectedEdge;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static void createVariant(Path original, Path target, double scale, int expectedEdge) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), TEMP_PREFIX, ".tmp");
        try {
            // A stream prevents Thumbnailator from appending .webp to the temporary pathname.
            try (OutputStream output = Files.newOutputStream(temporary)) {
                Thumbnails.of(original.toFile())
                        .scale(scale)
                        // Indexed GIF pixels need an explicit true-color buffer for WebP encoding.
                        .imageType(BufferedImage.TYPE_INT_ARGB)
                        .outputFormat("webp")
                        .outputQuality(OUTPUT_QUALITY)
                        .useExifOrientation(true)
                        .toOutputStream(output);
            }
            if (!isValidVariant(temporary, expectedEdge)) {
                throw new IOException("Encoded thumbnail is empty, invalid or has incorrect dimensions");
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (RuntimeException exception) {
            throw new IOException("Image conversion failed", exception);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static int originalLongEdge(Path original) throws IOException {
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
                return originalMaxEdge;
            } finally {
                reader.dispose();
            }
        }
    }

    public record BackfillSummary(int originalsVisited, int variantsGenerated, int variantsRepaired,
            int temporaryFilesCleaned, int failures) {
    }
}
