package com.eesara.drive.apikey;
import com.eesara.drive.apikey.dto.*;
import com.eesara.drive.audit.ServiceAudit;
import com.eesara.drive.common.ApiException;
import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.folder.repository.FolderRepository;
import com.eesara.drive.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.*;

@Service @RequiredArgsConstructor
public class ApiKeyAdminService {
 private final ApiKeyRepository keys; private final ApiKeyHasher hasher; private final UserRepository users;
 private final FolderRepository folders; private final ServiceAudit audit; private final SecureRandom random=new SecureRandom();
 @Value("${service.api-key.prefix:edrive_}") private String prefix;
 @Transactional public CreateApiKeyResponse create(CreateApiKeyRequest request){
  var user=users.findByUuid(request.userUuid()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","User not found."));
  if(request.scopes().stream().anyMatch(scope->!ApiKeyScope.valid(scope)))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SCOPE","One or more API-key scopes are invalid.");
  Folder folder=null;if(request.folderUuid()!=null&&!request.folderUuid().isBlank()){folder=folders.findByUuidAndOwner(request.folderUuid(),user).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"FOLDER_NOT_FOUND","Folder not found for this user."));}
  byte[] secret=new byte[32];random.nextBytes(secret);String slug=request.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_").replaceAll("^_|_$","");if(slug.isBlank())slug="service";
  String raw=prefix+slug+"_"+Base64.getUrlEncoder().withoutPadding().encodeToString(secret);String visible=raw.substring(0,Math.min(raw.length(),24));
  ApiKey key=keys.save(ApiKey.builder().name(request.name().trim()).keyHash(hasher.hash(raw)).keyPrefix(visible).user(user)
   .scopes(request.scopes().stream().sorted().collect(java.util.stream.Collectors.joining(","))).folder(folder).expiresAt(request.expiresAt()).build());
  audit.recordForKey(key,"API_KEY_CREATED",folder==null?null:folder.getUuid(),"SUCCESS",null);
  return new CreateApiKeyResponse(key.getId(),key.getName(),raw,key.getKeyPrefix(),key.scopeSet(),user.getUuid(),folder==null?null:folder.getUuid(),true,key.getExpiresAt(),key.getCreatedAt());
 }
 public List<ApiKeyResponse> list(){return keys.findAll().stream().map(this::response).toList();}
 @Transactional public ApiKeyResponse active(long id,boolean active){ApiKey key=find(id);key.setIsActive(active);keys.save(key);audit.recordForKey(key,active?"API_KEY_ACTIVATED":"API_KEY_REVOKED",null,"SUCCESS",null);return response(key);}
 private ApiKey find(long id){return keys.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"API_KEY_NOT_FOUND","API key not found."));}
 private ApiKeyResponse response(ApiKey k){return new ApiKeyResponse(k.getId(),k.getName(),k.getKeyPrefix(),k.scopeSet(),k.getUser().getUuid(),k.getFolder()==null?null:k.getFolder().getUuid(),Boolean.TRUE.equals(k.getIsActive()),k.getExpiresAt(),k.getLastUsedAt(),k.getCreatedAt());}
}
