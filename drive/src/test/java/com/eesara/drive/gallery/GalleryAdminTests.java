package com.eesara.drive.gallery;

import com.eesara.drive.common.ApiException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.assertj.core.api.Assertions.*;

class GalleryAdminTests {
    @TempDir Path root;
    GalleryService service;
    String folder = GalleryService.encode("Trip");
    String photo = GalleryService.encode("photo.jpg");
    @BeforeEach void setup() throws Exception {
        Files.writeString(root.resolve(".e-gallery-volume"), "disk");
        Files.createDirectory(root.resolve("Trip"));
        Files.writeString(root.resolve("Trip/photo.jpg"), "original");
        service = new GalleryService(root.toString(), "disk");
    }
    @Test void hiddenPhotoIsPrivateAndSettingsSurviveRestartAndRename() throws Exception {
        service.setCover(folder, photo);
        assertThat(service.galleries().getFirst().coverId()).isEqualTo(photo);
        service.renameMedia(folder, photo, "renamed.jpg");
        String renamed = GalleryService.encode("renamed.jpg");
        assertThat(service.cover(folder).id()).isEqualTo(renamed);
        service.hideMedia(folder, renamed, true);
        service = new GalleryService(root.toString(), "disk");
        assertThat(service.media(folder, 0, 100).items()).isEmpty();
        assertThat(service.media(folder, 0, 100, true).items().getFirst().hidden()).isTrue();
        assertThatThrownBy(() -> service.content(folder, renamed)).isInstanceOf(ApiException.class);
        assertThat(service.content(folder, renamed, true)).exists();
        assertThat(service.galleries().getFirst().coverId()).isNull();
        service.hideMedia(folder, renamed, false);
        assertThat(service.media(folder, 0, 100).total()).isEqualTo(1);
    }
    @Test void hideAndRenameFolderKeepAdminAccess() throws Exception {
        service.hideFolder(folder, true);
        assertThat(service.galleries()).isEmpty();
        assertThat(service.galleries(true).getFirst().hidden()).isTrue();
        assertThatThrownBy(() -> service.media(folder, 0, 100)).isInstanceOf(ApiException.class);
        String renamed = service.renameFolder(folder, "New Trip");
        assertThat(service.media(renamed, 0, 100, true).total()).isEqualTo(1);
        service.hideFolder(renamed, false);
        assertThat(service.galleries().getFirst().name()).isEqualTo("New Trip");
    }
    @Test void deletesAreRecoverableAndDoNotTouchOtherFolders() throws Exception {
        service.deleteMedia(folder, photo);
        assertThat(root.resolve("Trip/photo.jpg")).doesNotExist();
        try (var files = Files.list(root.resolve(".e-gallery-trash"))) {
            assertThat(Files.readString(files.findFirst().orElseThrow())).isEqualTo("original");
        }
        service.createFolder("Another");
        service.deleteFolder(folder);
        assertThat(service.galleries()).extracting(GalleryService.Gallery::name).containsExactly("Another");
    }
    @Test void uploadsRealImagesWithoutOverwritingAndRejectsUnsafePaths() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "jpg", out);
        var upload = new MockMultipartFile("file", "new.jpg", "image/jpeg", out.toByteArray());
        service.upload(folder, upload);
        assertThat(ImageIO.read(root.resolve("Trip/new.jpg").toFile()).getWidth()).isEqualTo(20);
        assertThatThrownBy(() -> service.upload(folder, upload)).isInstanceOf(FileAlreadyExistsException.class);
        assertThatThrownBy(() -> service.upload(folder, new MockMultipartFile("file", "fake.jpg", "image/jpeg", "<script>alert(1)</script>".getBytes()))).isInstanceOf(ApiException.class);
        for (String bad : new String[]{"../escape", "System Volume Information", "$RECYCLE.BIN", ".secret", "a/b", "bad:name", "a\\b"}) {
            assertThatThrownBy(() -> service.renameFolder(folder, bad)).isInstanceOf(ApiException.class);
        }
        Files.createSymbolicLink(root.resolve("Trip/link.jpg"), root.resolve(".e-gallery-volume"));
        assertThatThrownBy(() -> service.deleteMedia(folder, GalleryService.encode("link.jpg"))).isInstanceOf(ApiException.class);
        assertThat(Files.readString(root.resolve(".e-gallery-volume"))).isEqualTo("disk");
    }
    @Test void refusesWritesWhenDiskIsMissing() throws Exception {
        Files.delete(root.resolve(".e-gallery-volume"));
        assertThatThrownBy(() -> service.createFolder("Unsafe")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.deleteFolder(folder)).isInstanceOf(ApiException.class);
        assertThat(root.resolve("Trip/photo.jpg")).exists();
    }
}
