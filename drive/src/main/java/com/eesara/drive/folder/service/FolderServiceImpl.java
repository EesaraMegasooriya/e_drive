package com.eesara.drive.folder.service;

import com.eesara.drive.file.repository.DriveFileRepository;
import com.eesara.drive.file.service.DriveFileService;
import com.eesara.drive.folder.dto.CreateFolderRequest;
import com.eesara.drive.folder.dto.FolderResponse;
import com.eesara.drive.folder.dto.RenameFolderRequest;
import com.eesara.drive.folder.dto.UpdateFolderCoverRequest;
import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.folder.repository.FolderRepository;
import com.eesara.drive.security.service.CurrentUserService;
import com.eesara.drive.share.repository.ShareLinkRepository;
import com.eesara.drive.storage.StorageService;
import com.eesara.drive.user.entity.User;
import com.eesara.drive.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.net.URI;

@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final FolderRepository folderRepository;
    private final DriveFileRepository driveFileRepository;
    private final CurrentUserService currentUserService;
    private final ShareLinkRepository shareLinkRepository;
    private final StorageService storageService;
    private final UserRepository userRepository;
    private final DriveFileService driveFileService;

    @Override
    public FolderResponse createFolder(CreateFolderRequest request) {

        User owner = currentUserService.getCurrentUser();

        Folder parent = null;
        String path = "/" + request.getName().trim();

        if (request.getParentUuid() != null &&
                !request.getParentUuid().isBlank()) {

            parent = folderRepository.findByUuidAndOwner(
                    request.getParentUuid(),
                    owner
            ).orElseThrow(() ->
                    new RuntimeException("Parent folder not found"));

            path = parent.getPath() + "/" + request.getName().trim();
        }

        if (folderRepository.existsByParentAndNameAndOwner(
                parent,
                request.getName().trim(),
                owner
        )) {

            throw new RuntimeException("Folder already exists.");
        }

        Folder folder = Folder.builder()
                .name(request.getName().trim())
                .owner(owner)
                .parent(parent)
                .path(path)
                .coverImageUrl(normalizeImageUrl(request.getCoverImageUrl()))
                .coverIcon(normalizeIcon(request.getCoverIcon()))
                .build();

        folderRepository.save(folder);

        return toResponse(folder);
    }

    @Override
    public List<FolderResponse> listFolders(String parentUuid) {

        User owner = currentUserService.getCurrentUser();

        List<Folder> folders;

        if (parentUuid == null || parentUuid.isBlank()) {

            folders = folderRepository.findByParentAndOwner(
                    null,
                    owner
            );

        } else {

            Folder parent = folderRepository.findByUuidAndOwner(
                    parentUuid,
                    owner
            ).orElseThrow(() ->
                    new RuntimeException("Folder not found"));

            folders = folderRepository.findByParentAndOwner(
                    parent,
                    owner
            );
        }

        return folders.stream()
                .filter(folder -> !Boolean.TRUE.equals(folder.getIsDeleted()))
                .map(this::toResponse)
                .toList();
    }
    @Override
public FolderResponse renameFolder(
        String folderUuid,
        RenameFolderRequest request
) {

    User owner = currentUserService.getCurrentUser();

    Folder folder = folderRepository.findByUuidAndOwner(
            folderUuid,
            owner
    ).orElseThrow(() ->
            new RuntimeException("Folder not found"));

    String newName = request.getName().trim();

    if (folderRepository.existsByParentAndNameAndOwner(
            folder.getParent(),
            newName,
            owner
    ) && !folder.getName().equalsIgnoreCase(newName)) {

        throw new RuntimeException(
                "Folder with this name already exists."
        );
    }

    String oldPath = folder.getPath();

    String newPath;

    if (folder.getParent() == null) {

        newPath = "/" + newName;

    } else {

        newPath = folder.getParent().getPath() + "/" + newName;
    }

    folder.setName(newName);
    folder.setPath(newPath);

    folderRepository.save(folder);

    updateChildPaths(folder, oldPath, newPath);

    return toResponse(folder);
}

@Override
public FolderResponse getFolder(String folderUuid) {

    User owner = currentUserService.getCurrentUser();

    Folder folder = folderRepository.findByUuidAndOwner(
            folderUuid,
            owner
    ).orElseThrow(() ->
            new RuntimeException("Folder not found"));

    return toResponse(folder);
}
    @Override
    public FolderResponse setPublic(String folderUuid, boolean isPublic) {
        User owner = currentUserService.getCurrentUser();
        Folder folder = folderRepository.findByUuidAndOwner(folderUuid, owner)
                .orElseThrow(() -> new RuntimeException("Folder not found"));
        folder.setIsPublic(isPublic);
        return toResponse(folderRepository.save(folder));
    }

    @Override
    public FolderResponse moveFolder(String folderUuid, String parentUuid) {
        User owner = currentUserService.getCurrentUser();
        Folder source = ownedFolder(folderUuid, owner);
        Folder destination = resolveParent(parentUuid, owner);

        if (destination != null && belongsToTree(destination, source)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A folder cannot be moved into itself or one of its subfolders.");
        }
        if (java.util.Objects.equals(
                source.getParent() == null ? null : source.getParent().getId(),
                destination == null ? null : destination.getId())) {
            return toResponse(source);
        }
        ensureFolderNameAvailable(destination, source.getName(), owner);

        String oldPath = source.getPath();
        String newPath = destination == null
                ? "/" + source.getName()
                : destination.getPath() + "/" + source.getName();
        source.setParent(destination);
        source.setPath(newPath);
        folderRepository.save(source);
        updateChildPaths(source, oldPath, newPath);
        return toResponse(source);
    }

    @Override
    public FolderResponse copyFolder(String folderUuid, String parentUuid) {
        User owner = currentUserService.getCurrentUser();
        Folder source = ownedFolder(folderUuid, owner);
        Folder destination = resolveParent(parentUuid, owner);
        if (destination != null && belongsToTree(destination, source)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A folder cannot be copied into itself or one of its subfolders.");
        }
        ensureFolderNameAvailable(destination, source.getName(), owner);
        long copySize = folderTreeSize(source);
        if (copySize > Math.max(0L, owner.getStorageLimit() - owner.getUsedStorage())) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Storage quota exceeded");
        }
        return toResponse(copyFolderTree(source, destination, owner));
    }

    private long folderTreeSize(Folder folder) {
        long size = driveFileRepository.findByFolder(folder).stream()
                .filter(file -> !Boolean.TRUE.equals(file.getDeleted()))
                .mapToLong(file -> file.getFileSize())
                .sum();
        for (Folder child : folderRepository.findByParent(folder)) {
            if (!Boolean.TRUE.equals(child.getIsDeleted())) {
                size = Math.addExact(size, folderTreeSize(child));
            }
        }
        return size;
    }

    private Folder copyFolderTree(Folder source, Folder destination, User owner) {
        String path = destination == null
                ? "/" + source.getName()
                : destination.getPath() + "/" + source.getName();
        Folder copy = folderRepository.save(Folder.builder()
                .name(source.getName())
                .owner(owner)
                .parent(destination)
                .path(path)
                .coverImageUrl(source.getCoverImageUrl())
                .coverIcon(source.getCoverIcon())
                .isPublic(false)
                .build());

        for (var file : driveFileRepository.findByFolder(source)) {
            if (!Boolean.TRUE.equals(file.getDeleted())) {
                try {
                    driveFileService.copyFile(file.getUuid(), copy.getUuid());
                } catch (java.io.IOException exception) {
                    throw new RuntimeException("Unable to copy folder contents", exception);
                }
            }
        }
        for (Folder child : folderRepository.findByParent(source)) {
            if (!Boolean.TRUE.equals(child.getIsDeleted())) {
                copyFolderTree(child, copy, owner);
            }
        }
        return copy;
    }

    private Folder ownedFolder(String uuid, User owner) {
        return folderRepository.findByUuidAndOwner(uuid, owner)
                .filter(folder -> !Boolean.TRUE.equals(folder.getIsDeleted()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
    }

    private Folder resolveParent(String parentUuid, User owner) {
        return parentUuid == null || parentUuid.isBlank()
                ? null
                : ownedFolder(parentUuid, owner);
    }

    private boolean belongsToTree(Folder candidate, Folder ancestor) {
        Folder current = candidate;
        while (current != null) {
            if (current.getId().equals(ancestor.getId())) return true;
            current = current.getParent();
        }
        return false;
    }

    private void ensureFolderNameAvailable(Folder parent, String name, User owner) {
        if (folderRepository.existsByParentAndNameAndOwner(parent, name, owner)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A folder with this name already exists in the target folder.");
        }
    }
@Override
public void deleteFolder(String folderUuid) {

    User owner = currentUserService.getCurrentUser();

    Folder folder = folderRepository.findByUuidAndOwner(
            folderUuid,
            owner
    ).orElseThrow(() ->
            new RuntimeException("Folder not found"));

    long removedBytes = deleteRecursively(folder);
    owner.setUsedStorage(Math.max(0L, owner.getUsedStorage() - removedBytes));
    userRepository.save(owner);
}
private long deleteRecursively(Folder folder) {

    long removedBytes = 0L;

    for (Folder child : folderRepository.findByParent(folder)) {
        removedBytes += deleteRecursively(child);
    }

    for (var file : driveFileRepository.findByFolder(folder)) {
        shareLinkRepository.deleteAllByFile(file);
        try {
            storageService.delete(file.getStoragePath());
        } catch (java.io.IOException exception) {
            throw new RuntimeException("Unable to delete stored file", exception);
        }
        removedBytes += file.getFileSize();
        driveFileRepository.delete(file);
    }

    shareLinkRepository.deleteAllByFolder(folder);
    folderRepository.delete(folder);
    return removedBytes;
}
private FolderResponse toResponse(Folder folder) {

    return FolderResponse.builder()
            .uuid(folder.getUuid())
            .name(folder.getName())
            .parentUuid(
                    folder.getParent() == null
                            ? null
                            : folder.getParent().getUuid()
            )
            .path(folder.getPath())
            .coverImageUrl(folder.getCoverImageUrl())
            .coverIcon(folder.getCoverIcon())
            .totalFolders(0L)
            .totalFiles(0L)
            .createdAt(folder.getCreatedAt())
            .updatedAt(folder.getUpdatedAt())
            .isPublic(Boolean.TRUE.equals(folder.getIsPublic()))
            .build();
}

@Override
public FolderResponse updateCover(String folderUuid, UpdateFolderCoverRequest request) {
    User owner = currentUserService.getCurrentUser();
    Folder folder = ownedFolder(folderUuid, owner);
    String imageUrl = normalizeImageUrl(request.getCoverImageUrl());
    folder.setCoverImageUrl(imageUrl);
    folder.setCoverIcon(imageUrl == null ? normalizeIcon(request.getCoverIcon()) : null);
    return toResponse(folderRepository.save(folder));
}

private String normalizeImageUrl(String value) {
    if (value == null || value.isBlank()) return null;
    String trimmed = value.trim();
    try {
        URI uri = URI.create(trimmed);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalArgumentException();
        }
    } catch (IllegalArgumentException exception) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cover image must be a valid http or https URL");
    }
    if (trimmed.length() > 2048) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cover image URL is too long");
    }
    return trimmed;
}

private String normalizeIcon(String value) {
    if (value == null || value.isBlank()) return null;
    String trimmed = value.trim();
    if (trimmed.codePointCount(0, trimmed.length()) > 4) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder icon must contain at most 4 characters");
    }
    return trimmed;
}
private void updateChildPaths(
        Folder folder,
        String oldPath,
        String newPath
) {

    for (Folder child : folder.getChildren()) {

        String childOldPath = child.getPath();

        String childNewPath =
                newPath +
                childOldPath.substring(oldPath.length());

        child.setPath(childNewPath);

        folderRepository.save(child);

        updateChildPaths(
                child,
                childOldPath,
                childNewPath
        );
    }
}


}
