package com.eesara.drive.gallery;

import com.eesara.drive.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class GalleryThumbnailTests {
    @TempDir Path root;

    @Test void cachesPreviewsAndInvalidatesChangedOriginals() throws Exception {
        // A process fixture exercises invocation/caching without requiring FFmpeg locally.
        Path converter = root.resolve("converter");
        Files.writeString(converter, "#!/bin/sh\nfor arg do output=\"$arg\"; done\nprintf preview > \"$output\"\n");
        assertThat(converter.toFile().setExecutable(true)).isTrue();
        Path original = Files.writeString(root.resolve("photo.jpg"), "original");
        var service = new GalleryThumbnailService(root.resolve("cache").toString(), converter.toString());
        Path first = service.thumbnail(original);
        assertThat(Files.readString(first)).isEqualTo("preview");
        Files.delete(converter);
        assertThat(service.thumbnail(original)).isEqualTo(first);
        Files.writeString(original, "changed original");
        assertThatThrownBy(() -> service.thumbnail(original)).isInstanceOf(ApiException.class);
        try (var files = Files.list(root.resolve("cache"))) {
            assertThat(files.toList()).containsExactly(first);
        }
    }

    @Test void reportsMissingConverterWithoutWritingToMediaFolder() throws Exception {
        Path original = Files.writeString(root.resolve("video.mp4"), "video");
        var service = new GalleryThumbnailService(root.resolve("cache").toString(), root.resolve("missing").toString());
        assertThatThrownBy(() -> service.thumbnail(original)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("THUMBNAIL_UNAVAILABLE"));
        assertThat(Files.readString(original)).isEqualTo("video");
        try (var files = Files.list(root.resolve("cache"))) { assertThat(files.toList()).isEmpty(); }
    }
}
