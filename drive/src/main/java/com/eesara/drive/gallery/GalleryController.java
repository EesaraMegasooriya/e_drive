package com.eesara.drive.gallery;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/api/public/galleries")
@RequiredArgsConstructor
public class GalleryController {
    private final GalleryService galleries;
    private final GalleryPreviewService previews;

    @GetMapping
    public List<GalleryService.Gallery> list() { return galleries.galleries(); }

    @GetMapping("/{galleryId}/cover")
    public GalleryService.Media cover(@PathVariable String galleryId) throws IOException {
        return galleries.cover(galleryId);
    }

    @GetMapping("/{galleryId}/media")
    public GalleryService.Page media(@PathVariable String galleryId,
                                    @RequestParam(defaultValue = "0") int offset,
                                    @RequestParam(defaultValue = "100") int limit) {
        return galleries.media(galleryId, offset, limit);
    }

    @GetMapping("/{galleryId}/media/{mediaId}/preview")
    public ResponseEntity<Resource> preview(@PathVariable String galleryId, @PathVariable String mediaId,
            @RequestParam(defaultValue = "640") int size,
            @RequestParam(defaultValue = "") String v) throws IOException {
        if (size != 640 && size != 1920)
            throw new com.eesara.drive.common.ApiException(HttpStatus.BAD_REQUEST, "INVALID_PREVIEW_SIZE", "Use size 640 or 1920.");
        var source = galleries.content(galleryId, mediaId);
        var file = previews.preview(source, size);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .contentLength(Files.size(file))
                .eTag("\"" + file.getFileName() + "\"")
                .cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }

    // Spring MVC handles Range requests for this filesystem resource (206/416).
    @GetMapping("/{galleryId}/media/{mediaId}/content")
    public ResponseEntity<Resource> content(@PathVariable String galleryId, @PathVariable String mediaId, @RequestParam(defaultValue = "") String v) throws IOException {
        var file = galleries.content(galleryId, mediaId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(galleries.mime(file)))
                .contentLength(Files.size(file)).lastModified(Files.getLastModifiedTime(file).toMillis())
                .eTag("\"" + GalleryPreviewService.version(file) + "\"")
                .cacheControl(CacheControl.noCache()).header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.getFileName().toString(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file));
    }
}
