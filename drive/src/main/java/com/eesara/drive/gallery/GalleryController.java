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
    private final GalleryThumbnailService thumbnails;

    @GetMapping
    public List<GalleryService.Gallery> list() { return galleries.galleries(); }

    @GetMapping("/{galleryId}/media")
    public GalleryService.Page media(@PathVariable String galleryId,
                                    @RequestParam(defaultValue = "0") int offset,
                                    @RequestParam(defaultValue = "100") int limit) {
        return galleries.media(galleryId, offset, limit);
    }

    @GetMapping("/{galleryId}/media/{mediaId}/thumbnail")
    public ResponseEntity<Resource> thumbnail(@PathVariable String galleryId, @PathVariable String mediaId) {
        var file = thumbnails.thumbnail(galleries.content(galleryId, mediaId));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }

    // Spring MVC handles Range requests for this filesystem resource (206/416).
    @GetMapping("/{galleryId}/media/{mediaId}/content")
    public ResponseEntity<Resource> content(@PathVariable String galleryId, @PathVariable String mediaId) throws IOException {
        var file = galleries.content(galleryId, mediaId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(galleries.mime(file)))
                .contentLength(Files.size(file)).lastModified(Files.getLastModifiedTime(file).toMillis())
                .cacheControl(CacheControl.noCache()).header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.getFileName().toString(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file));
    }
}
