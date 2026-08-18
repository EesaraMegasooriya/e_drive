package com.eesara.drive.file.service;
import com.eesara.drive.apikey.ServiceAuthorization;
import com.eesara.drive.audit.ServiceAudit;
import com.eesara.drive.common.ApiException;
import com.eesara.drive.file.entity.DriveFile;
import com.eesara.drive.file.repository.DriveFileRepository;
import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.folder.repository.FolderRepository;
import com.eesara.drive.security.service.CurrentUserService;
import com.eesara.drive.share.repository.ShareLinkRepository;
import com.eesara.drive.storage.StorageService;
import com.eesara.drive.user.entity.*;
import com.eesara.drive.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class DriveFileServiceApiKeyTests {
 private DriveFileRepository files;private FolderRepository folders;private CurrentUserService current;private StorageService storage;private UserRepository users;private ServiceAuthorization auth;private ServiceImageValidator validator;private DriveFileServiceImpl service;private User owner;private Folder folder;
 @BeforeEach void setup()throws Exception{files=mock(DriveFileRepository.class);folders=mock(FolderRepository.class);current=mock(CurrentUserService.class);storage=mock(StorageService.class);users=mock(UserRepository.class);auth=mock(ServiceAuthorization.class);validator=mock(ServiceImageValidator.class);owner=User.builder().id(1L).uuid("11111111-1111-1111-1111-111111111111").email("owner@example.com").storageLimit(50_000_000L).usedStorage(0L).isActive(true).role(Role.USER).build();folder=Folder.builder().id(2L).uuid("22222222-2222-2222-2222-222222222222").owner(owner).name("production").path("/production").build();when(current.getCurrentUser()).thenReturn(owner);when(auth.uploadFolder(any())).thenReturn(folder.getUuid());when(auth.isApiKey()).thenReturn(true);when(folders.findByUuidAndOwner(folder.getUuid(),owner)).thenReturn(Optional.of(folder));when(storage.save(any())).thenReturn("aa/stored.webp");when(storage.checksum(any())).thenReturn("checksum");when(files.save(any())).thenAnswer(inv->{DriveFile f=inv.getArgument(0);if(f.getUuid()==null)f.setUuid("33333333-3333-3333-3333-333333333333");if(f.getCreatedAt()==null)f.setCreatedAt(java.time.LocalDateTime.now());return f;});service=new DriveFileServiceImpl(files,folders,current,storage,mock(ShareLinkRepository.class),users,auth,validator,mock(ServiceAudit.class));ReflectionTestUtils.setField(service,"publicBaseUrl","https://drive.eesara.com");}
 @Test void publicUploadReturnsStablePublicContract()throws Exception{var response=service.upload(new MockMultipartFile("file","photo.webp","image/webp",new byte[]{1,2,3}),folder.getUuid(),true);assertThat(response.getUuid()).isEqualTo("33333333-3333-3333-3333-333333333333");assertThat(response.getName()).isEqualTo("stored.webp");assertThat(response.getOriginalName()).isEqualTo("photo.webp");assertThat(response.getMimeType()).isEqualTo("image/webp");assertThat(response.getIsPublic()).isTrue();assertThat(response.getFolderUuid()).isEqualTo(folder.getUuid());assertThat(response.getUrl()).isEqualTo("https://drive.eesara.com/files/33333333-3333-3333-3333-333333333333/stored.webp");verify(validator).validate(any());}
 @Test void visibilityUpdateReturnsAnonymousUrl(){DriveFile file=DriveFile.builder().uuid("33333333-3333-3333-3333-333333333333").storedName("stored.webp").originalName("photo.webp").mimeType("image/webp").fileSize(3L).owner(owner).folder(folder).isPublic(false).build();when(files.findByUuid(file.getUuid())).thenReturn(Optional.of(file));when(files.save(file)).thenReturn(file);var response=service.setPublic(file.getUuid(),true);assertThat(response.getIsPublic()).isTrue();assertThat(response.getUrl()).endsWith("/files/"+file.getUuid()+"/stored.webp");}
 @Test void deletionIsIdempotentForServiceKey()throws Exception{when(files.findByUuid("44444444-4444-4444-4444-444444444444")).thenReturn(Optional.empty());service.deleteFile("44444444-4444-4444-4444-444444444444");verify(storage,never()).delete(any());}
 @Test void cannotDeleteAnotherUsersFile(){User other=User.builder().id(9L).email("other@example.com").build();DriveFile file=DriveFile.builder().uuid("55555555-5555-5555-5555-555555555555").owner(other).folder(folder).build();when(files.findByUuid(file.getUuid())).thenReturn(Optional.of(file));assertThatThrownBy(()->service.deleteFile(file.getUuid())).isInstanceOf(ApiException.class).extracting("code").isEqualTo("ACCESS_DENIED");}
}
