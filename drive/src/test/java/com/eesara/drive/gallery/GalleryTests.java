package com.eesara.drive.gallery;

import com.eesara.drive.common.ApiException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GalleryTests {
    @TempDir Path root;
    GalleryService service;
    String id = GalleryService.encode("Maskeliya Trip");
    @BeforeEach void setup() throws Exception {
        Files.writeString(root.resolve(".e-gallery-volume"), "6CAC-54E8\n");
        Files.createDirectory(root.resolve("Maskeliya Trip"));
        service = new GalleryService(root.toString(), "6CAC-54E8");
    }
    @Test void listsOnlyMediaAndPaginates() throws Exception {
        for (String name : new String[]{"a.jpg", "b.mp4", "test.txt", ".hidden.jpg"})
            Files.writeString(root.resolve("Maskeliya Trip").resolve(name), "data");
        assertThat(service.galleries()).containsExactly(new GalleryService.Gallery(id, "Maskeliya Trip"));
        var page = service.media(id, 1, 1);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items().getFirst().name()).isEqualTo("b.mp4");
        assertThatThrownBy(() -> service.media(id, -1, 1)).isInstanceOf(ApiException.class);
    }
    @Test void detectsMissingDisk() throws Exception {
        Files.delete(root.resolve(".e-gallery-volume"));
        assertThatThrownBy(service::galleries).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus().value()).isEqualTo(503));
    }
    @Test void blocksTraversalAndSymlinks() throws Exception {
        Files.createSymbolicLink(root.resolve("Maskeliya Trip/link.jpg"), root.resolve(".e-gallery-volume"));
        assertThat(service.media(id, 0, 100).items()).isEmpty();
        for (String name : new String[]{"../.e-gallery-volume", "/etc/passwd", "link.jpg"})
            assertThatThrownBy(() -> service.content(id, GalleryService.encode(name))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.media(GalleryService.encode(".."), 0, 100)).isInstanceOf(ApiException.class);
    }
    @Test void supportsVideoSeeking() throws Exception {
        Files.writeString(root.resolve("Maskeliya Trip/video.mp4"), "0123456789");
        var mvc = MockMvcBuilders.standaloneSetup(new GalleryController(service, new GalleryThumbnailService(root.resolve("cache").toString(), "ffmpeg"))).build();
        String url = "/api/public/galleries/" + id + "/media/" + GalleryService.encode("video.mp4") + "/content";
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().string("0123456789"));
        mvc.perform(get(url).header("Range", "bytes=2-5")).andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 2-5/10")).andExpect(content().string("2345"));
        mvc.perform(get(url).header("Range", "bytes=20-30")).andExpect(status().isRequestedRangeNotSatisfiable());
    }
}
