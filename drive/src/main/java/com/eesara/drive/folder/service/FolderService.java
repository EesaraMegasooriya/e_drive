package com.eesara.drive.folder.service;

import com.eesara.drive.folder.dto.CreateFolderRequest;
import com.eesara.drive.folder.dto.FolderResponse;
import com.eesara.drive.folder.dto.RenameFolderRequest;

import java.util.List;

public interface FolderService {

    FolderResponse createFolder(
            CreateFolderRequest request
    );

    FolderResponse renameFolder(
            String folderUuid,
            RenameFolderRequest request
    );

    void deleteFolder(
            String folderUuid
    );

    FolderResponse getFolder(
            String folderUuid
    );

    List<FolderResponse> listFolders(
            String parentUuid
    );

    FolderResponse setPublic(String folderUuid, boolean isPublic);

    FolderResponse moveFolder(String folderUuid, String parentUuid);

    FolderResponse copyFolder(String folderUuid, String parentUuid);

}
