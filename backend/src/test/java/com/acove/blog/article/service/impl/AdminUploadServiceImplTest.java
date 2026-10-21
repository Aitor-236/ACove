package com.acove.blog.article.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class AdminUploadServiceImplTest {
    @TempDir Path root;

    @Test
    void conversionFailurePreservesUploadedOriginalAndLogsWarning() throws Exception {
        var service = new AdminUploadServiceImpl();
        ReflectionTestUtils.setField(service, "uploadDir", root.toString());
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AdminUploadServiceImpl.class);
        var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            var result = service.uploadImage(
                    new MockMultipartFile("file", "bad.png", "image/png", "invalid image".getBytes()));
            assertEquals("invalid image", Files.readString(root.resolve(result.getUrl().substring("/uploads/".length()))));
            assertTrue(events.list.stream().anyMatch(event ->
                    event.getLevel() == ch.qos.logback.classic.Level.WARN
                    && event.getFormattedMessage().contains("Unable to generate display thumbnails")));
            try (var files = Files.walk(root)) {
                assertEquals(1, files.filter(Files::isRegularFile).count());
            }
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }
}
