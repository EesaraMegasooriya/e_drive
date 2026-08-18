package com.eesara.drive.file.controller;

import com.eesara.drive.file.dto.FileResponse;
import com.eesara.drive.file.dto.RenameFileRequest;
import com.eesara.drive.file.dto.UploadFileResponse;
import com.eesara.drive.file.service.DriveFileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final DriveFileService driveFileService;

    @PostMapping("/upload")
    public ResponseEntity<UploadFileResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String folderUuid,
            @RequestParam(required = false, defaultValue = "false") Boolean isPublic
    ) throws IOException {

        System.out.println("========== FILE UPLOAD ==========");
        System.out.println("Folder UUID = " + folderUuid);
        System.out.println("File = " + file.getOriginalFilename());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(driveFileService.upload(file, folderUuid, isPublic));
    }

    @GetMapping("/download/{uuid}")
    public ResponseEntity<Resource> download(
            @PathVariable String uuid
    ) throws IOException {

        var response = driveFileService.download(uuid);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(response.getMimeType()))
                .contentLength(response.getFileSize())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(response.getFileName())
                                .build()
                                .toString()
                )
                .body(response.getResource());
    }

    @GetMapping
    public ResponseEntity<List<FileResponse>> list(
            @RequestParam(required = false) String folderUuid
    ) {

        return ResponseEntity.ok(
                driveFileService.listFiles(folderUuid)
        );
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<FileResponse> rename(
            @PathVariable String uuid,
            @Valid @RequestBody RenameFileRequest request
    ) {

        return ResponseEntity.ok(
                driveFileService.renameFile(uuid, request)
        );
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(
            @PathVariable String uuid
    ) throws IOException {

        driveFileService.deleteFile(uuid);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{uuid}/move")
    public ResponseEntity<FileResponse> move(
            @PathVariable String uuid,
            @RequestParam(required = false) String folderUuid
    ) {

        return ResponseEntity.ok(
                driveFileService.moveFile(uuid, folderUuid)
        );
    }

    @PostMapping("/{uuid}/copy")
    public ResponseEntity<FileResponse> copy(
            @PathVariable String uuid,
            @RequestParam(required = false) String folderUuid
    ) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(driveFileService.copyFile(uuid, folderUuid));
    }

    @PutMapping("/{uuid}/visibility")
    public ResponseEntity<FileResponse> setVisibility(@PathVariable String uuid, @RequestParam boolean isPublic) {
        return ResponseEntity.ok(driveFileService.setPublic(uuid, isPublic));
    }
}
