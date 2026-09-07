package com.eesara.drive.gallery;

import com.eesara.drive.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Service
public class GalleryService {
    private final Path root;
    private final String marker;
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"), Map.entry("webp", "image/webp"),
            Map.entry("gif", "image/gif"), Map.entry("avif", "image/avif"),
            Map.entry("heic", "image/heic"), Map.entry("heif", "image/heif"),
            Map.entry("mp4", "video/mp4"), Map.entry("mov", "video/quicktime"),
            Map.entry("webm", "video/webm"), Map.entry("m4v", "video/mp4"),
            Map.entry("mkv", "video/x-matroska"));

    public GalleryService(@Value("${gallery.location:/mnt/e-gallery}") String location,
                          @Value("${gallery.marker:6CAC-54E8}") String marker) {
        this.root = Path.of(location).toAbsolutePath().normalize();
        this.marker = marker;
    }

    public record Gallery(String id, String name) {}
    public record Media(String id, String name, String type, String mimeType, long size,
                        long modifiedAt, String contentUrl) {}
    public record Page(List<Media> items, int offset, int limit, long total) {}

    // The marker lives on the external disk, distinguishing it from an empty mountpoint.
    private void requireStorage() {
        try {
            Path identity = root.resolve(".e-gallery-volume");
            if (!Files.isDirectory(root) || !Files.isReadable(root)
                    || !Files.isRegularFile(identity, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(identity) > 128
                    || !Files.readString(identity).trim().equals(marker)) throw unavailable();
        } catch (IOException e) { throw unavailable(); }
    }

    public List<Gallery> galleries() {
        requireStorage();
        try (var paths = Files.list(root)) {
            return paths.filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(p -> new Gallery(encode(p.getFileName().toString()), p.getFileName().toString()))
                    .toList();
        } catch (IOException e) { throw unavailable(); }
    }

    private Path gallery(String id) {
        requireStorage();
        String name = decode(id);
        if (name.startsWith(".") || name.contains("/") || name.contains("\\")) throw missing();
        Path folder = root.resolve(name).normalize();
        if (!folder.getParent().equals(root) || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) throw missing();
        return folder;
    }

    public Page media(String galleryId, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 200)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Use offset >= 0 and limit between 1 and 200.");
        Path folder = gallery(galleryId);
        try (var paths = Files.list(folder)) {
            List<Path> files = paths.filter(this::supported).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
            List<Media> items = new ArrayList<>();
            for (Path file : files.stream().skip(offset).limit(limit).toList()) {
                String name = file.getFileName().toString();
                String id = encode(name);
                String mime = mime(file);
                items.add(new Media(id, name, mime.startsWith("image/") ? "image" : "video", mime,
                        Files.size(file), Files.getLastModifiedTime(file).toMillis(),
                        "/api/public/galleries/" + galleryId + "/media/" + id + "/content"));
            }
            return new Page(items, offset, limit, files.size());
        } catch (IOException e) { throw unavailable(); }
    }

    public Path content(String galleryId, String mediaId) {
        Path folder = gallery(galleryId);
        String name = decode(mediaId);
        if (name.contains("/") || name.contains("\\")) throw missing();
        Path file = folder.resolve(name).normalize();
        if (!folder.equals(file.getParent()) || !supported(file)) throw missing();
        return file;
    }

    private boolean supported(Path file) {
        return !file.getFileName().toString().startsWith(".")
                && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && mime(file) != null;
    }

    public String mime(Path file) {
        String name = file.getFileName().toString();
        return TYPES.get(name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
    }

    static String encode(String name) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String id) {
        try {
            String name = new String(Base64.getUrlDecoder().decode(id), StandardCharsets.UTF_8);
            if (name.isBlank() || name.indexOf('\0') >= 0 || !encode(name).equals(id)) throw missing();
            return name;
        } catch (IllegalArgumentException e) { throw missing(); }
    }

    private ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "GALLERY_MEDIA_NOT_FOUND", "Gallery or media not found."); }
    private ApiException unavailable() { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GALLERY_STORAGE_UNAVAILABLE", "Gallery storage is unavailable."); }
}
