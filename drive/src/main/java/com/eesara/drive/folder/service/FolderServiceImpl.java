package com.eesara.drive.folder.service;

import com.eesara.drive.file.repository.DriveFileRepository;
import com.eesara.drive.folder.dto.CreateFolderRequest;
import com.eesara.drive.folder.dto.FolderResponse;
import com.eesara.drive.folder.dto.RenameFolderRequest;
import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.folder.repository.FolderRepository;
import com.eesara.drive.security.service.CurrentUserService;
import com.eesara.drive.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final FolderRepository folderRepository;
    private final DriveFileRepository driveFileRepository;
    private final CurrentUserService currentUserService;

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
public void deleteFolder(String folderUuid) {

    User owner = currentUserService.getCurrentUser();

    Folder folder = folderRepository.findByUuidAndOwner(
            folderUuid,
            owner
    ).orElseThrow(() ->
            new RuntimeException("Folder not found"));

    deleteRecursively(folder);
}
private void deleteRecursively(Folder folder) {

    for (Folder child : folder.getChildren()) {
        deleteRecursively(child);
    }

    folder.setIsDeleted(true);

    folderRepository.save(folder);
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
            .totalFolders(0L)
            .totalFiles(0L)
            .createdAt(folder.getCreatedAt())
            .updatedAt(folder.getUpdatedAt())
            .isPublic(Boolean.TRUE.equals(folder.getIsPublic()))
            .build();
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
