package com.eesara.drive.share.controller;

import com.eesara.drive.share.dto.AssetUrlResponse;
import com.eesara.drive.share.dto.CreateShareRequest;
import com.eesara.drive.share.dto.ShareResponse;
import com.eesara.drive.share.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    /**
     * Create (or return existing) share link.
     *
     * Returns:
     * - shareUrl
     * - assetUrl (for files)
     */
    @PostMapping
    public ResponseEntity<ShareResponse> createShare(
            @RequestBody CreateShareRequest request
    ) {

        return ResponseEntity.ok(
                shareService.createShare(request)
        );
    }

    /**
     * Returns the public direct asset URL for a single file.
     *
     * Example:
     * http://localhost:8080/api/public/assets/AbCdEf123.png
     */
    @GetMapping("/file/{uuid}")
    public ResponseEntity<AssetUrlResponse> getFileAssetUrl(
            @PathVariable String uuid
    ) {

        return ResponseEntity.ok(
                shareService.getFileAssetUrl(uuid)
        );
    }

    /**
     * Returns the public direct URLs for every file
     * inside a folder.
     */
    @GetMapping("/folder/{uuid}")
    public ResponseEntity<List<AssetUrlResponse>> getFolderAssetUrls(
            @PathVariable String uuid
    ) {

        return ResponseEntity.ok(
                shareService.getFolderAssetUrls(uuid)
        );
    }
}
