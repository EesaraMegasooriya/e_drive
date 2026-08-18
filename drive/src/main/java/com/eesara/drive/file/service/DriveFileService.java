package com.eesara.drive.file.service;

import com.eesara.drive.file.dto.FileDownloadResponse;
import com.eesara.drive.file.dto.FileResponse;
import com.eesara.drive.file.dto.RenameFileRequest;
import com.eesara.drive.file.dto.UploadFileResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface DriveFileService {

    UploadFileResponse upload(
            MultipartFile file,
            String folderUuid
    ) throws IOException;

    UploadFileResponse upload(MultipartFile file, String folderUuid, Boolean isPublic) throws IOException;

    FileDownloadResponse download(
            String fileUuid
    ) throws IOException;

    List<FileResponse> listFiles(
            String folderUuid
    );

    FileResponse setPublic(String fileUuid, boolean isPublic);

    FileResponse renameFile(
            String fileUuid,
            RenameFileRequest request
    );

    void deleteFile(
            String fileUuid
    ) throws IOException;

    FileResponse moveFile(
            String fileUuid,
            String folderUuid
    );

    FileResponse copyFile(String fileUuid, String folderUuid) throws IOException;
}
