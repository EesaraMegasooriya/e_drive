package com.eesara.drive.gallery;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/gallery-admin")
@RequiredArgsConstructor
public class GalleryAdminController {
    private final GalleryService galleries;
    private final GalleryPreviewService previews;
    private final GalleryAdminSecurity.Sessions sessions;
    public record Login(String username, String password) {}
    public record Name(String name) {}
    public record Visibility(boolean hidden) {}
    public record Cover(String mediaId) {}

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Login login) { return Map.of("token", sessions.login(login.username(), login.password())); }
    @GetMapping("/session")
    public Map<String, Boolean> session() { return Map.of("authenticated", true); }
    @PostMapping("/logout")
    public void logout(@RequestHeader("X-Gallery-Token") String token) { sessions.logout(token); }
    @GetMapping("/galleries")
    public List<GalleryService.Gallery> list() { return galleries.galleries(true); }
    @PostMapping("/galleries")
    public void create(@RequestBody Name request) throws IOException { galleries.createFolder(request.name()); }
    @GetMapping("/galleries/{id}/media")
    public GalleryService.Page media(@PathVariable String id, @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "100") int limit) { return galleries.media(id, offset, limit, true); }
    @PutMapping("/galleries/{id}/name")
    public Map<String, String> renameFolder(@PathVariable String id, @RequestBody Name request) throws IOException { return Map.of("id", galleries.renameFolder(id, request.name())); }
    @PutMapping("/galleries/{id}/visibility")
    public void hideFolder(@PathVariable String id, @RequestBody Visibility request) throws IOException { galleries.hideFolder(id, request.hidden()); }
    @PutMapping("/galleries/{id}/cover")
    public void cover(@PathVariable String id, @RequestBody Cover request) throws IOException { galleries.setCover(id, request.mediaId()); }
    @DeleteMapping("/galleries/{id}")
    public void deleteFolder(@PathVariable String id) throws IOException { galleries.deleteFolder(id); }
    @PostMapping("/galleries/{id}/media")
    public void upload(@PathVariable String id, @RequestParam("file") MultipartFile file) throws IOException { galleries.upload(id, file); }
    @PutMapping("/galleries/{id}/media/{mediaId}/name")
    public void renameMedia(@PathVariable String id, @PathVariable String mediaId, @RequestBody Name request) throws IOException { galleries.renameMedia(id, mediaId, request.name()); }
    @PutMapping("/galleries/{id}/media/{mediaId}/visibility")
    public void hideMedia(@PathVariable String id, @PathVariable String mediaId, @RequestBody Visibility request) throws IOException { galleries.hideMedia(id, mediaId, request.hidden()); }
    @DeleteMapping("/galleries/{id}/media/{mediaId}")
    public void deleteMedia(@PathVariable String id, @PathVariable String mediaId) throws IOException { galleries.deleteMedia(id, mediaId); }
    @GetMapping("/galleries/{id}/media/{mediaId}/preview")
    public ResponseEntity<Resource> preview(@PathVariable String id, @PathVariable String mediaId, @RequestParam(defaultValue = "640") int size) {
        if (size != 640 && size != 1920) throw new com.eesara.drive.common.ApiException(HttpStatus.BAD_REQUEST, "INVALID_SIZE", "Use 640 or 1920.");
        var file = previews.preview(galleries.content(id, mediaId, true), size);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).body(new FileSystemResource(file));
    }
    @GetMapping("/galleries/{id}/media/{mediaId}/content")
    public ResponseEntity<Resource> content(@PathVariable String id, @PathVariable String mediaId) throws IOException {
        var file = galleries.content(id, mediaId, true);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(galleries.mime(file))).contentLength(Files.size(file)).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(file));
    }
    @ExceptionHandler(FileAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> conflict() { return ResponseEntity.status(409).body(Map.of("message", "That name already exists. Choose another name.")); }
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> io() { return ResponseEntity.status(503).body(Map.of("message", "The drive could not complete this operation. Check the mount, free space and write permissions.")); }
}
