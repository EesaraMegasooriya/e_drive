package com.eesara.drive.apikey;

import com.eesara.drive.apikey.dto.CreateApiKeyRequest;
import com.eesara.drive.audit.*;
import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.folder.repository.FolderRepository;
import com.eesara.drive.user.entity.*;
import com.eesara.drive.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApiKeySecurityTests {
 @Test void hashesKeysWithoutRetainingPlaintext(){ApiKeyHasher h=new ApiKeyHasher();String raw="edrive_test_abcdefghijklmnopqrstuvwxyz0123456789";assertThat(h.hash(raw)).hasSize(64).doesNotContain(raw);assertThat(h.hash(raw)).isEqualTo(h.hash(raw));}

 @Test void creationReturnsPlaintextOnceAndPersistenceContainsOnlyHash(){
  ApiKeyRepository keys=mock(ApiKeyRepository.class);UserRepository users=mock(UserRepository.class);FolderRepository folders=mock(FolderRepository.class);ServiceAudit audit=mock(ServiceAudit.class);
  User user=User.builder().id(1L).uuid("11111111-1111-1111-1111-111111111111").email("owner@example.com").name("Owner").password("x").role(Role.USER).isActive(true).storageLimit(100L).usedStorage(0L).build();
  Folder folder=Folder.builder().id(2L).uuid("22222222-2222-2222-2222-222222222222").name("production").path("/Applications/Wishes/production").owner(user).build();
  when(users.findByUuid(user.getUuid())).thenReturn(Optional.of(user));when(folders.findByUuidAndOwner(folder.getUuid(),user)).thenReturn(Optional.of(folder));
  when(keys.save(any())).thenAnswer(inv->{ApiKey k=inv.getArgument(0);k.setId(3L);k.setCreatedAt(LocalDateTime.now());return k;});
  ApiKeyAdminService service=new ApiKeyAdminService(keys,new ApiKeyHasher(),users,folders,audit);ReflectionTestUtils.setField(service,"prefix","edrive_");
  var created=service.create(new CreateApiKeyRequest("Wishes Production",user.getUuid(),Set.of("files:upload","files:update"),folder.getUuid(),null));
  var captor=org.mockito.ArgumentCaptor.forClass(ApiKey.class);verify(keys).save(captor.capture());ApiKey stored=captor.getValue();
  assertThat(created.key()).startsWith("edrive_wishes_production_");assertThat(stored.getKeyHash()).isEqualTo(new ApiKeyHasher().hash(created.key()));assertThat(stored.getKeyHash()).doesNotContain(created.key());
  when(keys.findAll()).thenReturn(List.of(stored));assertThat(service.list().getFirst().keyPrefix()).isEqualTo(created.keyPrefix());
 }
}
