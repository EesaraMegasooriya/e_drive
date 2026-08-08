package com.eesara.drive.drive.service;

import com.eesara.drive.drive.dto.DriveResponse;
import com.eesara.drive.file.dto.FileResponse;
import com.eesara.drive.file.service.DriveFileService;
import com.eesara.drive.folder.dto.FolderResponse;
import com.eesara.drive.folder.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DriveServiceImpl implements DriveService {

    private final FolderService folderService;
    private final DriveFileService driveFileService;

    @Override
    public DriveResponse getDrive(String folderUuid) {

        FolderResponse currentFolder = null;

        if (folderUuid != null && !folderUuid.isBlank()) {
            currentFolder = folderService.getFolder(folderUuid);
        }

        List<FolderResponse> folders =
                folderService.listFolders(folderUuid);

        List<FileResponse> files =
                driveFileService.listFiles(folderUuid);

        return DriveResponse.builder()
                .currentFolder(currentFolder)
                .folders(folders)
                .files(files)
                .build();
    }
}