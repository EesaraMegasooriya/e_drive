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
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.eesara.drive.share.repository.ShareLinkRepository;

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

    @Override
    public UploadFileResponse upload(
            MultipartFile file,
            String folderUuid) throws IOException {

        User owner = currentUserService.getCurrentUser();

        Folder folder = null;

        if (folderUuid != null && !folderUuid.isBlank()) {
            folder = folderRepository.findByUuid(folderUuid)
                    .orElseThrow(() -> new RuntimeException("Folder not found"));
        }

        if (folder != null) {

    System.out.println("Folder ID = " + folder.getId());
    System.out.println("Owner ID = " + owner.getId());
    System.out.println("Filename = " + file.getOriginalFilename());

    List<DriveFile> files =
            driveFileRepository.findByFolderAndOwner(folder, owner);

    System.out.println("Files in folder:");

    for (DriveFile f : files) {
        System.out.println(
                f.getId() + " | " +
                f.getOriginalName()
        );
    }

    if (driveFileRepository.existsByFolderAndOriginalNameAndOwner(
            folder,
            file.getOriginalFilename(),
            owner)) {

        throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "File already exists."
);
    }
}

        String storagePath = storageService.save(file);

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
                .fileSize(file.getSize())
                .storagePath(storagePath)
                .build();

        driveFileRepository.save(driveFile);

        return UploadFileResponse.builder()
                .uuid(driveFile.getUuid())
                .originalName(driveFile.getOriginalName())
                .fileSize(driveFile.getFileSize())
                .message("File uploaded successfully")
                .build();
    }

    @Override
    public FileDownloadResponse download(String fileUuid) {

        User owner = currentUserService.getCurrentUser();

        DriveFile driveFile = driveFileRepository.findByUuid(fileUuid)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!driveFile.getOwner().getId().equals(owner.getId())) {
            throw new RuntimeException("Access denied");
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

        List<DriveFile> files;

        if (folderUuid == null || folderUuid.isBlank()) {

            files = driveFileRepository.findByFolderIsNullAndOwner(owner);

        } else {

            Folder folder = folderRepository.findByUuid(folderUuid)
                    .orElseThrow(() -> new RuntimeException("Folder not found"));

            files = driveFileRepository.findByFolderAndOwner(
                    folder,
                    owner);
        }

        return files.stream()
                .map(this::toFileResponse)
                .toList();
    }

    @Override
    public FileResponse setPublic(String fileUuid, boolean isPublic) {
        User owner = currentUserService.getCurrentUser();
        DriveFile file = driveFileRepository.findByUuid(fileUuid)
                .orElseThrow(() -> new RuntimeException("File not found"));
        if (!file.getOwner().getId().equals(owner.getId())) throw new RuntimeException("Access denied");
        file.setIsPublic(isPublic);
        return toFileResponse(driveFileRepository.save(file));
    }

    @Override
    public FileResponse renameFile(
            String fileUuid,
            RenameFileRequest request) {

        User owner = currentUserService.getCurrentUser();

        DriveFile driveFile = driveFileRepository.findByUuid(fileUuid)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!driveFile.getOwner().getId().equals(owner.getId())) {
            throw new RuntimeException("Access denied");
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

    DriveFile driveFile = driveFileRepository.findByUuid(fileUuid)
            .orElseThrow(() -> new RuntimeException("File not found"));

    if (!driveFile.getOwner().getId().equals(owner.getId())) {
        throw new RuntimeException("Access denied");
    }

    // Delete all share links first
    shareLinkRepository.deleteAllByFile(driveFile);

    // Delete physical file
    storageService.delete(driveFile.getStoragePath());

    // Delete database record
    driveFileRepository.delete(driveFile);
}

    @Override
    public FileResponse moveFile(
            String fileUuid,
            String folderUuid) {

        User owner = currentUserService.getCurrentUser();

        DriveFile driveFile = driveFileRepository.findByUuid(fileUuid)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!driveFile.getOwner().getId().equals(owner.getId())) {
            throw new RuntimeException("Access denied");
        }

        Folder folder = null;

        if (folderUuid != null && !folderUuid.isBlank()) {

            folder = folderRepository.findByUuid(folderUuid)
                    .orElseThrow(() ->
                            new RuntimeException("Folder not found"));
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
                .build();
    }
}
