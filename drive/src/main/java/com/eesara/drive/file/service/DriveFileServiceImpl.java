package com.eesara.drive.file.service;

import com.eesara.drive.file.dto.FileDownloadResponse;
import com.eesara.drive.file.dto.FileResponse;
import com.eesara.drive.file.dto.RenameFileRequest;
import com.eesara.drive.file.dto.UploadFileResponse;
import com.eesara.drive.file.entity.DriveFile;
import com.eesara.drive.file.repository.DriveFileRepository;
import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.folder.repository.FolderRepository;
import com.eesara.drive.security.service.CurrentUserService;
import com.eesara.drive.storage.StorageService;
import com.eesara.drive.user.entity.User;
import com.eesara.drive.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.eesara.drive.share.repository.ShareLinkRepository;
import com.eesara.drive.apikey.ServiceAuthorization;
import com.eesara.drive.apikey.ApiKeyScope;
import com.eesara.drive.audit.ServiceAudit;
import org.springframework.beans.factory.annotation.Value;
import com.eesara.drive.common.ApiException;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DriveFileServiceImpl implements DriveFileService {

    private final DriveFileRepository driveFileRepository;
    private final FolderRepository folderRepository;
    private final CurrentUserService currentUserService;
    private final StorageService storageService;
    private final ShareLinkRepository shareLinkRepository;
    private final UserRepository userRepository;
    private final ServiceAuthorization authorization;
    private final ServiceImageValidator imageValidator;
    private final ServiceAudit audit;
    @Value("${public.base-url:${app.base-url:http://localhost:8080}}") private String publicBaseUrl;

    @Override
    public UploadFileResponse upload(
            MultipartFile file,
            String folderUuid) throws IOException {
        return upload(file, folderUuid, false);
    }

    @Override
    public UploadFileResponse upload(MultipartFile file, String folderUuid, Boolean isPublic) throws IOException {

        folderUuid = authorization.uploadFolder(folderUuid);
        if (authorization.isApiKey()) imageValidator.validate(file);

        User owner = currentUserService.getCurrentUser();
        // Multipart implementations backed by a temporary path may move that
        // path during save(), so capture immutable metadata before storage.
        long uploadedFileSize = file.getSize();

        long remainingStorage = Math.max(0L, owner.getStorageLimit() - owner.getUsedStorage());
        if (uploadedFileSize > remainingStorage) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Storage quota exceeded");
        }

        Folder folder = null;

        if (folderUuid != null && !folderUuid.isBlank()) {
            folder = folderRepository.findByUuidAndOwner(folderUuid, owner)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Folder not found"
                    ));
        }

        if (driveFileRepository.existsByFolderAndOriginalNameAndOwner(
                folder, file.getOriginalFilename(), owner)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "File already exists.");
        }

        String storagePath = storageService.save(file);

        String checksum = null;
        if (uploadedFileSize <= 64L * 1024 * 1024) {
            checksum = storageService.checksum(storagePath);
            if (driveFileRepository.existsByOwnerAndChecksumAndDeletedFalse(owner, checksum)) {
                storageService.delete(storagePath);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This file content already exists.");
            }
        }

        String originalName = file.getOriginalFilename();

        String extension = "";

        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(
                    originalName.lastIndexOf(".") + 1);
        }

        String storedName = storagePath.substring(
                storagePath.lastIndexOf("/") + 1);

        DriveFile driveFile = DriveFile.builder()
                .owner(owner)
                .folder(folder)
                .originalName(originalName)
                .storedName(storedName)
                .mimeType(file.getContentType())
                .extension(extension)
                .fileSize(uploadedFileSize)
                .storagePath(storagePath)
                .checksum(checksum)
                .isPublic(Boolean.TRUE.equals(isPublic))
                .build();

        driveFileRepository.save(driveFile);
        owner.setUsedStorage(owner.getUsedStorage() + uploadedFileSize);
        userRepository.save(owner);

        if (authorization.isApiKey()) audit.record("UPLOAD_COMPLETED", driveFile.getUuid(), "SUCCESS",
                folder == null ? null : folder.getUuid());

        return UploadFileResponse.builder()
                .uuid(driveFile.getUuid())
                .originalName(driveFile.getOriginalName())
                .name(driveFile.getStoredName())
                .mimeType(driveFile.getMimeType())
                .size(driveFile.getFileSize())
                .fileSize(driveFile.getFileSize())
                .url(Boolean.TRUE.equals(driveFile.getIsPublic()) ? publicUrl(driveFile) : null)
                .isPublic(Boolean.TRUE.equals(driveFile.getIsPublic()))
                .folderUuid(folder == null ? null : folder.getUuid())
                .createdAt(driveFile.getCreatedAt())
                .message("File uploaded successfully")
                .build();
    }

    @Override
    public FileDownloadResponse download(String fileUuid) {

        User owner = currentUserService.getCurrentUser();

        DriveFile driveFile = driveFileRepository.findByUuid(fileUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));

        authorization.file(driveFile, ApiKeyScope.FILES_READ.value());

        if (!driveFile.getOwner().getId().equals(owner.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Access denied.");
        }

        Resource resource = storageService.loadAsResource(
                driveFile.getStoragePath());

        return FileDownloadResponse.builder()
                .resource(resource)
                .fileName(driveFile.getOriginalName())
                .mimeType(driveFile.getMimeType())
                .fileSize(driveFile.getFileSize())
                .build();
    }

    @Override
    public List<FileResponse> listFiles(String folderUuid) {

        User owner = currentUserService.getCurrentUser();

        authorization.scope(ApiKeyScope.FILES_READ.value());
        if ((folderUuid == null || folderUuid.isBlank()) && authorization.restrictedFolderUuid() != null)
            folderUuid = authorization.restrictedFolderUuid();
        List<DriveFile> files;

        if (folderUuid == null || folderUuid.isBlank()) {

            files = driveFileRepository.findByFolderIsNullAndOwner(owner);

        } else {

            Folder folder = folderRepository.findByUuidAndOwner(folderUuid, owner)
                    .orElseThrow(() -> new RuntimeException("Folder not found"));

            authorization.folder(folder, ApiKeyScope.FILES_READ.value());

            files = driveFileRepository.findByFolderAndOwner(
                    folder,
                    owner);
        }

        return files.stream()
                .filter(file -> !Boolean.TRUE.equals(file.getDeleted()))
                .map(this::toFileResponse)
                .toList();
    }

    @Override
    public FileResponse setPublic(String fileUuid, boolean isPublic) {
        User owner = currentUserService.getCurrentUser();
        DriveFile file = driveFileRepository.findByUuid(fileUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        authorization.file(file, ApiKeyScope.FILES_UPDATE.value());
        if (!file.getOwner().getId().equals(owner.getId())) throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Access denied.");
        file.setIsPublic(isPublic);
        FileResponse response=toFileResponse(driveFileRepository.save(file));
        if(authorization.isApiKey())audit.record("VISIBILITY_CHANGED",fileUuid,"SUCCESS",String.valueOf(isPublic));
        return response;
    }

    @Override
    public FileResponse renameFile(
            String fileUuid,
            RenameFileRequest request) {

        User owner = currentUserService.getCurrentUser();

        DriveFile driveFile = driveFileRepository.findByUuid(fileUuid)
                .orElseThrow(() -> new RuntimeException("File not found"));

        authorization.file(driveFile, ApiKeyScope.FILES_UPDATE.value());

        if (!driveFile.getOwner().getId().equals(owner.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Access denied.");
        }

        String newName = request.getName().trim();

        Folder folder = driveFile.getFolder();

        if (driveFileRepository.existsByFolderAndOriginalNameAndOwner(
                folder,
                newName,
                owner) && !driveFile.getOriginalName().equalsIgnoreCase(newName)) {

            throw new RuntimeException("A file with this name already exists.");
        }

        String extension = "";

        if (newName.contains(".")) {
            extension = newName.substring(
                    newName.lastIndexOf(".") + 1);
        }

        driveFile.setOriginalName(newName);
        driveFile.setExtension(extension);

        driveFileRepository.save(driveFile);

        return toFileResponse(driveFile);
    }

    @Override
public void deleteFile(String fileUuid) throws IOException {

    User owner = currentUserService.getCurrentUser();

    authorization.scope(ApiKeyScope.FILES_DELETE.value());
    DriveFile driveFile = driveFileRepository.findByUuid(fileUuid).orElse(null);
    if (driveFile == null) { if (authorization.isApiKey()) return; throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File not found"); }
    authorization.file(driveFile, ApiKeyScope.FILES_DELETE.value());

    if (!driveFile.getOwner().getId().equals(owner.getId())) {
        throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Access denied.");
    }

    // Delete all share links first
    shareLinkRepository.deleteAllByFile(driveFile);

    // Delete physical file
    storageService.delete(driveFile.getStoragePath());

    // Delete database record
    driveFileRepository.delete(driveFile);
    owner.setUsedStorage(Math.max(0L, owner.getUsedStorage() - driveFile.getFileSize()));
    userRepository.save(owner);
    if(authorization.isApiKey())audit.record("FILE_DELETED",fileUuid,"SUCCESS",null);
}

    @Override
    public FileResponse moveFile(
            String fileUuid,
            String folderUuid) {

        User owner = currentUserService.getCurrentUser();

        DriveFile driveFile = driveFileRepository.findByUuid(fileUuid)
                .orElseThrow(() -> new RuntimeException("File not found"));

        authorization.file(driveFile, ApiKeyScope.FILES_UPDATE.value());

        if (!driveFile.getOwner().getId().equals(owner.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Access denied.");
        }

        Folder folder = null;

        if (folderUuid != null && !folderUuid.isBlank()) {

            folder = folderRepository.findByUuidAndOwner(folderUuid, owner)
                    .orElseThrow(() ->
                            new RuntimeException("Folder not found"));
            authorization.folder(folder, ApiKeyScope.FILES_UPDATE.value());
        }
        if (folder == null && authorization.restrictedFolderUuid() != null)
            authorization.folder(null, ApiKeyScope.FILES_UPDATE.value());

        String currentFolderUuid = driveFile.getFolder() == null
                ? null : driveFile.getFolder().getUuid();
        String targetFolderUuid = folder == null ? null : folder.getUuid();
        if (java.util.Objects.equals(currentFolderUuid, targetFolderUuid)) {
            return toFileResponse(driveFile);
        }

        if (driveFileRepository.existsByFolderAndOriginalNameAndOwner(
                folder,
                driveFile.getOriginalName(),
                owner)) {

            throw new RuntimeException("A file with this name already exists in the target folder.");
        }

        driveFile.setFolder(folder);

        driveFileRepository.save(driveFile);

        return toFileResponse(driveFile);
    }

    @Override
    public FileResponse copyFile(String fileUuid, String folderUuid) throws IOException {
        User owner = currentUserService.getCurrentUser();
        DriveFile source = driveFileRepository.findByUuid(fileUuid)
                .filter(file -> file.getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        authorization.file(source, ApiKeyScope.FILES_UPDATE.value());

        Folder destination = null;
        if (folderUuid != null && !folderUuid.isBlank()) {
            destination = folderRepository.findByUuidAndOwner(folderUuid, owner)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
            authorization.folder(destination, ApiKeyScope.FILES_UPDATE.value());
        }
        if (driveFileRepository.existsByFolderAndOriginalNameAndOwner(
                destination, source.getOriginalName(), owner)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A file with this name already exists in the target folder.");
        }
        if (source.getFileSize() > Math.max(0L, owner.getStorageLimit() - owner.getUsedStorage())) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Storage quota exceeded");
        }

        String copiedPath = storageService.copy(source.getStoragePath());
        try {
            DriveFile copy = DriveFile.builder()
                    .owner(owner)
                    .folder(destination)
                    .originalName(source.getOriginalName())
                    .storedName(copiedPath.substring(copiedPath.lastIndexOf('/') + 1))
                    .mimeType(source.getMimeType())
                    .extension(source.getExtension())
                    .fileSize(source.getFileSize())
                    .storagePath(copiedPath)
                    .checksum(source.getChecksum())
                    .isPublic(false)
                    .build();
            driveFileRepository.save(copy);
            owner.setUsedStorage(owner.getUsedStorage() + copy.getFileSize());
            userRepository.save(owner);
            return toFileResponse(copy);
        } catch (RuntimeException exception) {
            storageService.delete(copiedPath);
            throw exception;
        }
    }

    private FileResponse toFileResponse(DriveFile driveFile) {

        Folder folder = driveFile.getFolder();

        return FileResponse.builder()
                .uuid(driveFile.getUuid())
                .originalName(driveFile.getOriginalName())
                .mimeType(driveFile.getMimeType())
                .extension(driveFile.getExtension())
                .fileSize(driveFile.getFileSize())
                .folderUuid(
                        folder == null
                                ? null
                                : folder.getUuid())
                .createdAt(driveFile.getCreatedAt())
                .isPublic(Boolean.TRUE.equals(driveFile.getIsPublic()))
                .url(Boolean.TRUE.equals(driveFile.getIsPublic()) ? publicUrl(driveFile) : null)
                .build();
    }

    private String publicUrl(DriveFile file) {
        return publicBaseUrl.replaceAll("/$", "") + "/files/" + file.getUuid() + "/" + file.getStoredName();
    }
}
