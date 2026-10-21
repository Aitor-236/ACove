package com.acove.blog.common.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.stream.Stream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ImageThumbnailServiceTest {
    @TempDir Path root;

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg", "gif", "webp"})
    void realEncodingPreservesOriginalAndProducesOnlyDecodableVariants(String format) throws Exception {
        for (int[] dimensions : new int[][] {{2400, 1800}, {1800, 2400}, {120, 80}}) {
            Path original = root.resolve(format + dimensions[0] + "." + format);
            writeImage(original, format, dimensions[0], dimensions[1], Color.RED);
            byte[] bytes = Files.readAllBytes(original);
            assertEquals(2, ImageThumbnailService.generateMissingVariants(original));
            for (int edge : new int[] {480, 1600}) {
                Path variant = ImageThumbnailService.variantPath(original, edge);
                assertTrue(Files.size(variant) > 0);
                BufferedImage decoded = ImageIO.read(variant.toFile());
                assertNotNull(decoded);
                Color pixel = new Color(decoded.getRGB(decoded.getWidth() / 2, decoded.getHeight() / 2));
                assertTrue(pixel.getRed() > 200 && pixel.getBlue() < 50, format + " color: " + pixel);
                assertEquals(Math.min(edge, Math.max(dimensions[0], dimensions[1])),
                        Math.max(decoded.getWidth(), decoded.getHeight()));
                assertEquals((double) dimensions[0] / dimensions[1],
                        (double) decoded.getWidth() / decoded.getHeight(), 0.02);
            }
            assertArrayEquals(bytes, Files.readAllBytes(original));
        }
        try (Stream<Path> files = Files.list(root)) {
            assertEquals(9, files.count(), "No .tmp.webp or other sidecar files may remain");
        }
    }

    @Test
    void repairsEmptyCorruptWrongFormatAndWrongSizeButKeepsValidFiles() throws Exception {
        Path original = root.resolve("original.png");
        writeImage(original, "png", 800, 600, Color.RED);
        Path small = ImageThumbnailService.variantPath(original, 480);
        Path large = ImageThumbnailService.variantPath(original, 1600);
        Files.createFile(small);
        Files.writeString(large, "corrupt");
        assertEquals(2, ImageThumbnailService.generateMissingVariants(original));
        writeImage(small, "png", 480, 360, Color.RED);
        writeImage(large, "webp", 1600, 1200, Color.RED);
        assertEquals(2, ImageThumbnailService.generateMissingVariants(original));
        byte[] smallBytes = Files.readAllBytes(small);
        FileTime oldTime = FileTime.fromMillis(1000);
        Files.setLastModifiedTime(small, oldTime);
        Files.setLastModifiedTime(large, oldTime);
        assertEquals(0, ImageThumbnailService.generateMissingVariants(original));
        assertArrayEquals(smallBytes, Files.readAllBytes(small));
        assertEquals(oldTime, Files.getLastModifiedTime(small));
        assertEquals(oldTime, Files.getLastModifiedTime(large));
    }

    @Test
    void animatedGifUsesFirstFrame() throws Exception {
        Path original = root.resolve("animated.gif");
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(original.toFile())) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            writer.writeToSequence(new IIOImage(image(40, 30, Color.RED), null, null), null);
            writer.writeToSequence(new IIOImage(image(40, 30, Color.BLUE), null, null), null);
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        byte[] bytes = Files.readAllBytes(original);
        ImageThumbnailService.generateMissingVariants(original);
        BufferedImage decoded = ImageIO.read(ImageThumbnailService.variantPath(original, 480).toFile());
        Color pixel = new Color(decoded.getRGB(20, 15));
        assertTrue(pixel.getRed() > 200 && pixel.getBlue() < 50, "Decoded first frame: " + pixel
                + "; original first frame: " + new Color(ImageIO.read(original.toFile()).getRGB(20, 15)));
        assertArrayEquals(bytes, Files.readAllBytes(original));
    }

    @Test
    void offlineBackfillCleansLegacyLeaksRepairsAndIsIdempotent() throws Exception {
        Path directory = Files.createDirectories(root.resolve("gallery/nested"));
        Path original = directory.resolve("one.png");
        writeImage(original, "png", 600, 400, Color.RED);
        Files.createFile(ImageThumbnailService.variantPath(original, 480));
        for (String name : new String[] {".acove-thumb-a.tmp", ".acove-thumb-a.tmp.webp",
                ".acove-thumb-a.tmp.w480.webp", ".acove-thumb-a.tmp.w1600.webp"}) {
            Files.writeString(directory.resolve(name), "leak");
            assertFalse(ImageThumbnailService.isSupportedOriginal(directory.resolve(name)));
        }
        Path unrelated = directory.resolve("notes.txt");
        Files.writeString(unrelated, "keep");
        var first = ImageThumbnailService.backfill(root);
        assertEquals(new ImageThumbnailService.BackfillSummary(1, 1, 1, 4, 0), first);
        assertEquals(new ImageThumbnailService.BackfillSummary(1, 0, 0, 0, 0),
                ImageThumbnailService.backfill(root));
        assertEquals("keep", Files.readString(unrelated));
    }

    @Test
    void failureCleansEncodedTemporaryAndPreservesExistingTarget() throws Exception {
        Path original = root.resolve("one.png");
        writeImage(original, "png", 600, 400, Color.RED);
        Path target = Files.createDirectory(ImageThumbnailService.variantPath(original, 480));
        Files.writeString(target.resolve("keep"), "keep");
        assertThrows(java.io.IOException.class, () -> ImageThumbnailService.generateMissingVariants(original));
        assertEquals("keep", Files.readString(target.resolve("keep")));
        try (Stream<Path> files = Files.list(root)) {
            assertEquals(2, files.count());
        }
    }

    @Test
    void corruptOriginalIsReportedAndDoesNotCreateVariants() throws Exception {
        Path original = Files.createDirectories(root.resolve("article")).resolve("bad.png");
        Files.writeString(original, "corrupt input");
        assertThrows(java.io.IOException.class, () -> ImageThumbnailService.generateMissingVariants(original));
        assertEquals(1, ImageThumbnailService.backfill(root).failures());
        try (Stream<Path> files = Files.list(original.getParent())) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void truncatedWebpIsRepairedAndTransparencyIsPreserved() throws Exception {
        Path original = root.resolve("transparent.png");
        BufferedImage image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(50, 40, Color.RED.getRGB());
        assertTrue(ImageIO.write(image, "png", original.toFile()));
        ImageThumbnailService.generateMissingVariants(original);
        Path small = ImageThumbnailService.variantPath(original, 480);
        byte[] bytes = Files.readAllBytes(small);
        Files.write(small, java.util.Arrays.copyOf(bytes, Math.min(24, bytes.length / 2)));
        assertEquals(1, ImageThumbnailService.generateMissingVariants(original));
        BufferedImage decoded = ImageIO.read(small.toFile());
        assertEquals(0, (decoded.getRGB(0, 0) >>> 24));
        assertEquals(255, (decoded.getRGB(50, 40) >>> 24));
    }

    @Test
    void partialBackfillSuccessIsCountedWhenSecondVariantFails() throws Exception {
        Path directory = Files.createDirectories(root.resolve("article"));
        Path original = directory.resolve("one.png");
        writeImage(original, "png", 600, 400, Color.RED);
        Path target = Files.createDirectory(ImageThumbnailService.variantPath(original, 1600));
        Files.writeString(target.resolve("keep"), "keep");
        var summary = ImageThumbnailService.backfill(root);
        assertEquals(new ImageThumbnailService.BackfillSummary(1, 1, 0, 0, 1), summary);
        try (Stream<Path> files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".acove-thumb-")));
        }
    }

    @Test
    void unavailableWebpEncoderFailsAndCleansTemporaryFile() throws Exception {
        Path original = root.resolve("one.png");
        writeImage(original, "png", 600, 400, Color.RED);
        byte[] bytes = Files.readAllBytes(original);
        var registry = javax.imageio.spi.IIORegistry.getDefaultInstance();
        var providers = new java.util.ArrayList<javax.imageio.spi.ImageWriterSpi>();
        registry.getServiceProviders(javax.imageio.spi.ImageWriterSpi.class, true).forEachRemaining(provider -> {
            if (java.util.Arrays.stream(provider.getFormatNames()).anyMatch("webp"::equalsIgnoreCase)) {
                providers.add(provider);
            }
        });
        assertFalse(providers.isEmpty());
        try {
            providers.forEach(registry::deregisterServiceProvider);
            assertThrows(java.io.IOException.class, () -> ImageThumbnailService.generateMissingVariants(original));
            assertArrayEquals(bytes, Files.readAllBytes(original));
            try (Stream<Path> files = Files.list(root)) {
                assertEquals(1, files.count());
            }
        } finally {
            providers.forEach(registry::registerServiceProvider);
        }
    }

    private static void writeImage(Path path, String format, int width, int height, Color color) throws Exception {
        assertTrue(ImageIO.write(image(width, height, color), format, path.toFile()));
    }

    private static BufferedImage image(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
