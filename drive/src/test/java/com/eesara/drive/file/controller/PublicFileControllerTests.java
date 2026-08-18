package com.eesara.drive.file.controller;
import com.eesara.drive.file.entity.DriveFile;
import com.eesara.drive.file.repository.DriveFileRepository;
import com.eesara.drive.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
class PublicFileControllerTests {
 @Test void servesPublicImageAnonymouslyWithImmutableCorsHeaders(){DriveFileRepository files=mock(DriveFileRepository.class);StorageService storage=mock(StorageService.class);DriveFile file=DriveFile.builder().uuid("11111111-1111-1111-1111-111111111111").storedName("stored.webp").originalName("card.webp").mimeType("image/webp").fileSize(3L).isPublic(true).deleted(false).storagePath("aa/stored.webp").build();when(files.findByUuid(file.getUuid())).thenReturn(Optional.of(file));when(storage.loadAsResource(file.getStoragePath())).thenReturn(new ByteArrayResource(new byte[]{1,2,3}));var response=new PublicFileController(files,storage).get(file.getUuid(),file.getStoredName());assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/webp");assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin")).isEqualTo("*");assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");assertThat(response.getHeaders().getCacheControl()).contains("immutable");}
}
