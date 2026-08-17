package com.eesara.drive.file.service;

import com.eesara.drive.file.dto.BulkOperationRequest;
import com.eesara.drive.file.entity.DriveFile;
import com.eesara.drive.file.repository.DriveFileRepository;
import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.folder.repository.FolderRepository;
import com.eesara.drive.folder.service.FolderService;
import com.eesara.drive.security.service.CurrentUserService;
import com.eesara.drive.storage.StorageService;
import com.eesara.drive.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class BulkFileService {
    private final DriveFileService fileService;
    private final FolderService folderService;
    private final DriveFileRepository files;
    private final FolderRepository folders;
    private final CurrentUserService currentUserService;
    private final StorageService storage;

    public void move(BulkOperationRequest request) {
        validateSelection(request);
        User owner = currentUserService.getCurrentUser();
        List<Folder> selectedFolders = topLevelFolders(request, owner);
        selectedFilesOutsideFolders(request, owner, selectedFolders)
                .forEach(file -> fileService.moveFile(file.getUuid(), request.getDestinationFolderUuid()));
        selectedFolders.forEach(folder -> folderService.moveFolder(folder.getUuid(), request.getDestinationFolderUuid()));
    }

    public void copy(BulkOperationRequest request) throws IOException {
        validateSelection(request);
        User owner = currentUserService.getCurrentUser();
        List<Folder> selectedFolders = topLevelFolders(request, owner);
        for (DriveFile file : selectedFilesOutsideFolders(request, owner, selectedFolders)) {
            fileService.copyFile(file.getUuid(), request.getDestinationFolderUuid());
        }
        selectedFolders.forEach(folder -> folderService.copyFolder(folder.getUuid(), request.getDestinationFolderUuid()));
    }

    public void delete(BulkOperationRequest request) throws IOException {
        validateSelection(request);
        User owner = currentUserService.getCurrentUser();
        List<Folder> selectedFolders = topLevelFolders(request, owner);
        for (DriveFile file : selectedFilesOutsideFolders(request, owner, selectedFolders)) {
            fileService.deleteFile(file.getUuid());
        }
        selectedFolders.forEach(folder -> folderService.deleteFolder(folder.getUuid()));
    }

    @Transactional(readOnly = true)
    public void writeZip(BulkOperationRequest request, User owner, OutputStream output) throws IOException {
        List<Folder> selectedFolders = topLevelFolders(request, owner);
        List<DriveFile> selectedFiles = selectedFilesOutsideFolders(request, owner, selectedFolders);
        if (selectedFiles.isEmpty() && selectedFolders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one item");
        }
        Set<String> entries = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (DriveFile file : selectedFiles) addFile(zip, file, safe(file.getOriginalName()), entries);
            for (Folder folder : selectedFolders) addFolder(zip, folder, safe(folder.getName()), entries);
            zip.finish();
        }
    }

    private void addFolder(ZipOutputStream zip, Folder folder, String path, Set<String> entries) throws IOException {
        for (DriveFile file : files.findByFolder(folder)) {
            if (!Boolean.TRUE.equals(file.getDeleted())) addFile(zip, file, path + "/" + safe(file.getOriginalName()), entries);
        }
        for (Folder child : folders.findByParent(folder)) {
            if (!Boolean.TRUE.equals(child.getIsDeleted())) addFolder(zip, child, path + "/" + safe(child.getName()), entries);
        }
    }

    private void addFile(ZipOutputStream zip, DriveFile file, String requestedName, Set<String> entries) throws IOException {
        String entryName = uniqueName(requestedName, entries);
        zip.putNextEntry(new ZipEntry(entryName));
        Files.copy(storage.load(file.getStoragePath()), zip);
        zip.closeEntry();
    }

    private String uniqueName(String name, Set<String> entries) {
        if (entries.add(name)) return name;
        int index = 2;
        while (!entries.add(name + " (" + index + ")")) index++;
        return name + " (" + index + ")";
    }

    private String safe(String name) {
        return name.replace('\\', '_').replace('/', '_').replace("..", "_");
    }

    private void validateSelection(BulkOperationRequest request) {
        if (request.getFileUuids().isEmpty() && request.getFolderUuids().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one item");
        }
    }

    private List<Folder> topLevelFolders(BulkOperationRequest request, User owner) {
        List<Folder> selected = request.getFolderUuids().stream().distinct()
                .map(uuid -> ownedFolder(uuid, owner)).toList();
        Set<Long> ids = selected.stream().map(Folder::getId).collect(java.util.stream.Collectors.toSet());
        return selected.stream().filter(folder -> {
            Folder parent = folder.getParent();
            while (parent != null) {
                if (ids.contains(parent.getId())) return false;
                parent = parent.getParent();
            }
            return true;
        }).toList();
    }

    private List<DriveFile> selectedFilesOutsideFolders(
            BulkOperationRequest request, User owner, List<Folder> selectedFolders) {
        Set<Long> folderIds = selectedFolders.stream().map(Folder::getId)
                .collect(java.util.stream.Collectors.toSet());
        return request.getFileUuids().stream().distinct().map(uuid -> ownedFile(uuid, owner))
                .filter(file -> {
                    Folder folder = file.getFolder();
                    while (folder != null) {
                        if (folderIds.contains(folder.getId())) return false;
                        folder = folder.getParent();
                    }
                    return true;
                }).toList();
    }

    private DriveFile ownedFile(String uuid, User owner) {
        return files.findByUuid(uuid).filter(file -> file.getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
    }

    private Folder ownedFolder(String uuid, User owner) {
        return folders.findByUuidAndOwner(uuid, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
    }
}
