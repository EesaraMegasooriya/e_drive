package com.eesara.drive.share.controller;

import com.eesara.drive.share.entity.ShareLink;
import com.eesara.drive.share.entity.ShareType;
import com.eesara.drive.share.service.ShareService;
import com.eesara.drive.share.dto.PublicFolderFileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicShareController {

    private final ShareService shareService;

    /**
     * Lists every non-deleted file in a shared folder, including files in nested folders.
     * Each item includes its original filename, extension, path, and public asset URL.
     */
    @GetMapping("/folders/{token}")
    public ResponseEntity<List<PublicFolderFileResponse>> getFolderContents(
            @PathVariable String token,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(shareService.getPublicFolderFiles(token, offset, limit));
    }

    /** Unlimited public API for integrations that need every direct file URL. */
    @GetMapping("/folders/{token}/links")
    public ResponseEntity<List<PublicFolderFileResponse>> getAllFolderLinks(
            @PathVariable String token
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(shareService.getAllPublicFolderFiles(token));
    }

    @GetMapping("/folders/{token}/assets/{fileUuid}.{extension}")
    public ResponseEntity<Resource> getFolderAsset(
            @PathVariable String token,
            @PathVariable String fileUuid,
            @PathVariable String extension
    ) {
        var file = shareService.getSharedFolderDownload(token, fileUuid);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMimeType()))
                .contentLength(file.getFileSize())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.getFileName()).build().toString()
                )
                .body(file.getResource());
    }

    /**
     * Canonical direct URL. It does not depend on the filename extension, so
     * extensionless and renamed files continue to work.
     */
    @GetMapping("/folders/{token}/assets/{fileUuid}/content")
    public ResponseEntity<Resource> getFolderAsset(
            @PathVariable String token,
            @PathVariable String fileUuid
    ) {
        var file = shareService.getSharedFolderDownload(token, fileUuid);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMimeType()))
                .contentLength(file.getFileSize())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.getFileName()).build().toString()
                )
                .body(file.getResource());
    }

    @GetMapping("/assets/{token}.{extension}")
    public ResponseEntity<Resource> getAsset(

            @PathVariable String token,

            @PathVariable String extension

    ) {

        ShareLink share = shareService.getByToken(token);

        if (share.getType() != ShareType.FILE) {
            throw new RuntimeException("Folder sharing is not supported.");
        }

        // Validate requested extension
        if (!share.getFile().getExtension().equalsIgnoreCase(extension)) {
            throw new RuntimeException("Invalid file extension.");
        }

        Resource resource = shareService.getSharedResource(token);

        return ResponseEntity.ok()

                .contentType(
                        MediaType.parseMediaType(
                                share.getFile().getMimeType()
                        )
                )

                .contentLength(
                        share.getFile().getFileSize()
                )

                .header(HttpHeaders.ACCEPT_RANGES, "bytes")

                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(
                                        share.getFile().getOriginalName()
                                )
                                .build()
                                .toString()
                )

                .body(resource);
    }

}
