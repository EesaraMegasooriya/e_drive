package com.eesara.drive.share.controller;

import com.eesara.drive.share.entity.ShareLink;
import com.eesara.drive.share.entity.ShareType;
import com.eesara.drive.share.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicShareController {

    private final ShareService shareService;

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