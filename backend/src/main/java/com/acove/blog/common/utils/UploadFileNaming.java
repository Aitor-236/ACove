package com.acove.blog.common.utils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Shared naming convention for newly uploaded resource files. */
public final class UploadFileNaming {

    private static final DateTimeFormatter UTC_SECOND_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private UploadFileNaming() {
    }

    public static String create(Module module, String extension) {
        return create(module, extension, Instant.now(Clock.systemUTC()));
    }

    static String create(Module module, String extension, Instant instant) {
        return module.value + "-" + UUID.randomUUID() + "-" + UTC_SECOND_FORMAT.format(instant)
                + "." + extension;
    }

    public enum Module {
        AVATAR("avatar"),
        HERO("hero"),
        FAVICON("favicon"),
        ARTICLE("article"),
        GALLERY("gallery");

        private final String value;

        Module(String value) {
            this.value = value;
        }
    }
}
