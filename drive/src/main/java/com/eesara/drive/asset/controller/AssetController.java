package com.eesara.drive.asset.controller;

import com.eesara.drive.file.entity.DriveFile;
import com.eesara.drive.file.repository.DriveFileRepository;
import com.eesara.drive.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final DriveFileRepository driveFileRepository;
    private final StorageService storageService;

    @GetMapping("/{uuid}.{extension}")
    public ResponseEntity<Resource> getAsset(
            @PathVariable String uuid,
            @PathVariable String extension
    ) {

        DriveFile file = driveFileRepository.findByUuid(uuid)
                .orElseThrow(() ->
                        new RuntimeException("File not found"));

        // Prevent someone requesting a different extension
        if (!file.getExtension().equalsIgnoreCase(extension)) {
            throw new RuntimeException("Invalid file extension");
        }

        Resource resource = storageService.loadAsResource(
                file.getStoragePath()
        );

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMimeType()))
                .contentLength(file.getFileSize())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(file.getOriginalName())
                                .build()
                                .toString()
                )
                .body(resource);
    }
}