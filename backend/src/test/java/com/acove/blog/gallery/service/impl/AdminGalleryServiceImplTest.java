package com.acove.blog.gallery.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.acove.blog.gallery.entity.GalleryImage;
import com.acove.blog.gallery.service.GalleryService;
import com.acove.blog.gallery.service.GalleryTransactionService;

class AdminGalleryServiceImplTest {
    @TempDir Path root;
    GalleryTransactionService transactions;
    AdminGalleryServiceImpl service;

    @BeforeEach
    void setUp() {
        transactions = mock(GalleryTransactionService.class);
        service = new AdminGalleryServiceImpl(mock(GalleryService.class), transactions);
        ReflectionTestUtils.setField(service, "uploadDir", root.toString());
        when(transactions.addImage(anyLong(), anyString(), anyString())).thenAnswer(invocation -> {
            GalleryImage image = new GalleryImage();
            image.setId(1L);
            image.setImageUrl(invocation.getArgument(1));
            return image;
        });
    }

    @Test
    void singleImageDeletionRemovesOriginalAndBothVariants() throws Exception {
        String url = service.uploadImage(1L, png()).getUrl();
        assertFileCount(3);
        when(transactions.deleteImage(1L)).thenReturn(url);
        service.deleteImage(1L);
        assertFileCount(0);
    }

    @Test
    void galleryDeletionRemovesAllOriginalsAndVariants() throws Exception {
        String first = service.uploadImage(1L, png()).getUrl();
        String second = service.uploadImage(1L, png()).getUrl();
        assertFileCount(6);
        when(transactions.deleteGallery(1L)).thenReturn(List.of(first, second));
        service.deleteGallery(1L);
        assertFileCount(0);
    }

    @Test
    void databaseFailureRollsBackOriginalAndBothVariants() throws Exception {
        when(transactions.addImage(anyLong(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("simulated database failure"));
        assertThrows(IllegalStateException.class, () -> service.uploadImage(1L, png()));
        assertFileCount(0);
    }

    @Test
    void conversionFailureKeepsOriginalForFallback() throws Exception {
        String url = service.uploadImage(1L,
                new MockMultipartFile("file", "bad.png", "image/png", "invalid image".getBytes())).getUrl();
        assertFileCount(1);
        assertEquals("invalid image", Files.readString(root.resolve(url.substring("/uploads/".length()))));
    }

    private MockMultipartFile png() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB), "png", output));
        return new MockMultipartFile("file", "test.png", "image/png", output.toByteArray());
    }

    private void assertFileCount(long expected) throws Exception {
        try (var files = Files.walk(root)) {
            assertEquals(expected, files.filter(Files::isRegularFile).count());
        }
    }
}
