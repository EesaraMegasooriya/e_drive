package com.eesara.drive.apikey;
import com.eesara.drive.audit.ServiceAudit;
import com.eesara.drive.common.ApiException;
import com.eesara.drive.file.entity.DriveFile;
import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.folder.repository.FolderRepository;
import com.eesara.drive.user.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ServiceAuthorizationTests {
 private FolderRepository folders;private ServiceAuthorization authorization;private User owner;private Folder root,child,outside;
 @BeforeEach void setup(){folders=mock(FolderRepository.class);authorization=new ServiceAuthorization(folders,mock(ServiceAudit.class));owner=User.builder().id(1L).email("owner@example.com").role(Role.USER).build();root=Folder.builder().id(1L).uuid("11111111-1111-1111-1111-111111111111").owner(owner).name("production").path("/production").build();child=Folder.builder().id(2L).uuid("22222222-2222-2222-2222-222222222222").owner(owner).parent(root).name("images").path("/production/images").build();outside=Folder.builder().id(3L).uuid("33333333-3333-3333-3333-333333333333").owner(owner).name("outside").path("/outside").build();ApiKey key=ApiKey.builder().id(7L).user(owner).folder(root).scopes("files:upload,files:delete").build();SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthenticationToken(new ApiKeyPrincipal(key)));when(folders.findByUuid(child.getUuid())).thenReturn(Optional.of(child));when(folders.findByUuid(outside.getUuid())).thenReturn(Optional.of(outside));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void defaultsUploadToRestrictedFolder(){assertThat(authorization.uploadFolder(null)).isEqualTo(root.getUuid());}
 @Test void allowsDescendantAndRejectsOutside(){assertThat(authorization.uploadFolder(child.getUuid())).isEqualTo(child.getUuid());assertThatThrownBy(()->authorization.uploadFolder(outside.getUuid())).isInstanceOf(ApiException.class).extracting("code").isEqualTo("FOLDER_RESTRICTED");}
 @Test void rejectsMissingScope(){DriveFile file=DriveFile.builder().uuid("44444444-4444-4444-4444-444444444444").owner(owner).folder(child).build();assertThatThrownBy(()->authorization.file(file,"files:update")).isInstanceOf(ApiException.class).extracting("code").isEqualTo("INSUFFICIENT_SCOPE");}
 @Test void rejectsDeleteOutsideTree(){DriveFile file=DriveFile.builder().uuid("44444444-4444-4444-4444-444444444444").owner(owner).folder(outside).build();assertThatThrownBy(()->authorization.file(file,"files:delete")).isInstanceOf(ApiException.class).extracting("code").isEqualTo("FOLDER_RESTRICTED");}
}
