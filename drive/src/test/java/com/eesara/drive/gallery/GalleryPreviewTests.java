package com.eesara.drive.gallery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GalleryPreviewTests {
    @TempDir Path root;

    private GalleryPreviewService fixture() throws Exception {
        // Real child process, deterministic output; FFmpeg codecs are deployment-tested.
        Path executable = root.resolve("preview-fixture");
        Files.writeString(executable, "#!/bin/sh\nfor arg do output=\"$arg\"; done\nprintf preview > \"$output\"\n");
        assertThat(executable.toFile().setExecutable(true)).isTrue();
        return new GalleryPreviewService(root.resolve("cache").toString(), executable.toString());
    }

    @Test void cachesSizesSeparatelyAndInvalidatesReplacedPhotos() throws Exception {
        var previews = fixture();
        Path source = Files.writeString(root.resolve("photo.jpg"), "original");
        var first = previews.preview(source, 640);
        assertThat(previews.preview(source, 640)).isEqualTo(first);
        assertThat(previews.preview(source, 1920)).isNotEqualTo(first);
        Files.writeString(source, "replacement photo");
        assertThat(previews.preview(source, 640)).isNotEqualTo(first);
        assertThat(Files.readString(source)).isEqualTo("replacement photo");
    }

    @Test void conditionalRequestsAndVersionedCacheHeaders() throws Exception {
        var previews = fixture();
        Files.writeString(root.resolve(".e-gallery-volume"), "6CAC-54E8");
        Path folder = Files.createDirectory(root.resolve("Trip"));
        Path source = Files.writeString(folder.resolve("photo.jpg"), "original");
        var service = new GalleryService(root.toString(), "6CAC-54E8");
        var mvc = MockMvcBuilders.standaloneSetup(new GalleryController(service, previews)).build();
        String url = "/api/public/galleries/" + GalleryService.encode("Trip") + "/media/" + GalleryService.encode("photo.jpg") + "/preview";
        var response = mvc.perform(get(url).param("v", GalleryPreviewService.version(source)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-cache"))
                .andReturn().getResponse();
        mvc.perform(get(url).header("If-None-Match", response.getHeader("ETag"))).andExpect(status().isNotModified());
        mvc.perform(get(url).param("v", "stale")).andExpect(header().string("Cache-Control", "no-cache"));
        // Endpoint checks source availability even when a preview was previously cached.
        Files.delete(root.resolve(".e-gallery-volume"));
        assertThatThrownBy(() -> service.content(GalleryService.encode("Trip"), GalleryService.encode("photo.jpg")))
                .isInstanceOf(com.eesara.drive.common.ApiException.class);
    }

    @Test void cacheCleanupKeepsRecentPreviews() throws Exception {
        var previews = fixture();
        Path source = Files.writeString(root.resolve("photo.jpg"), "original");
        Path old = previews.preview(source, 640);
        Path recent = previews.preview(source, 1920);
        Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(
                System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(31)));
        previews.cleanCache();
        assertThat(Files.exists(old)).isFalse();
        assertThat(Files.exists(recent)).isTrue();
    }
}
