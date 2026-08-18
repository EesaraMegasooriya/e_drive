package com.eesara.drive.apikey;
import com.eesara.drive.audit.ServiceAudit;
import com.eesara.drive.common.ApiException;
import com.eesara.drive.file.entity.DriveFile;
import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.folder.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class ServiceAuthorization {
 private final FolderRepository folders; private final ServiceAudit audit;
 public ApiKeyPrincipal principal(){Object p=SecurityContextHolder.getContext().getAuthentication().getPrincipal();return p instanceof ApiKeyPrincipal key?key:null;}
 public boolean isApiKey(){return principal()!=null;}
 public String restrictedFolderUuid(){ApiKeyPrincipal key=principal();return key==null?null:key.getFolderUuid();}
 public void scope(String required){ApiKeyPrincipal key=principal();if(key!=null&&!key.getScopes().contains(required)){audit.record("SCOPE_VIOLATION",null,"REJECTED",required);throw new ApiException(HttpStatus.FORBIDDEN,"INSUFFICIENT_SCOPE","The API key lacks scope: "+required);}}
 public String uploadFolder(String requested){ApiKeyPrincipal key=principal();if(key==null)return requested;scope(ApiKeyScope.FILES_UPLOAD.value());String target=requested==null||requested.isBlank()?key.getFolderUuid():requested;
  if(key.getFolderUuid()!=null){if(target==null||(!target.equals(key.getFolderUuid())&&!folderAllowed(target,key))){denyFolder(target);}}return target;}
 public void folder(Folder folder,String scope){ApiKeyPrincipal key=principal();if(key==null)return;scope(scope);if(key.getFolderUuid()!=null&&(folder==null||!isDescendant(folder,key.getFolderUuid())))denyFolder(folder==null?null:folder.getUuid());}
 public void file(DriveFile file,String scope){ApiKeyPrincipal key=principal();if(key==null)return;scope(scope);if(key.getFolderUuid()!=null&&(file.getFolder()==null||!isDescendant(file.getFolder(),key.getFolderUuid())))denyFolder(file.getUuid());}
 public boolean folderAllowed(String uuid,ApiKeyPrincipal key){Folder folder=folders.findByUuid(uuid).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"FOLDER_NOT_FOUND","Folder not found."));return folder.getOwner().getId().equals(key.getUser().getId())&&isDescendant(folder,key.getFolderUuid());}
 private boolean isDescendant(Folder folder,String root){for(Folder current=folder;current!=null;current=current.getParent())if(current.getUuid().equals(root))return true;return false;}
 private void denyFolder(String target){audit.record("FOLDER_RESTRICTION_VIOLATION",target,"REJECTED",null);throw new ApiException(HttpStatus.FORBIDDEN,"FOLDER_RESTRICTED","The API key cannot access this folder or file.");}
}
