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
    private static boolean hiddenFolder(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith(".") || lower.startsWith("$")
                || Set.of("system volume information", "recycler", "recycled", "lost+found").contains(lower);
    }
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

    public record Gallery(String id, String name, boolean hidden, String coverId) {
        public Gallery(String id, String name) { this(id, name, false, null); }
    }
    public record Media(String id, String name, String type, String mimeType, long size,
                        long modifiedAt, String contentUrl, boolean hidden) {}
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

    public List<Gallery> galleries() { return galleries(false); }

    public List<Gallery> galleries(boolean admin) {
        requireStorage();
        try (var paths = Files.list(root)) {
            List<Gallery> result = new ArrayList<>();
            for (Path p : paths.filter(p -> !hiddenFolder(p.getFileName().toString()))
                    .filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                var meta = metadata(p);
                boolean hidden = Boolean.parseBoolean(meta.getProperty("hidden"));
                if (!admin && hidden) continue;
                String cover = meta.getProperty("cover");
                if (cover != null && (cover.contains("/") || cover.contains("\\") || !supported(p.resolve(cover)) || isHidden(meta, cover))) cover = null;
                result.add(new Gallery(encode(p.getFileName().toString()), p.getFileName().toString(),
                        hidden, cover == null ? null : encode(cover)));
            }
            return result;
        } catch (IOException e) { throw unavailable(); }
    }

    private Path gallery(String id) { return gallery(id, false); }

    private Path gallery(String id, boolean admin) {
        requireStorage();
        String name = decode(id);
        if (hiddenFolder(name) || name.contains("/") || name.contains("\\")) throw missing();
        Path folder = root.resolve(name).normalize();
        if (!folder.getParent().equals(root) || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) throw missing();
        if (!admin && Boolean.parseBoolean(metadata(folder).getProperty("hidden"))) throw missing();
        return folder;
    }

    public Page media(String galleryId, int offset, int limit) { return media(galleryId, offset, limit, false); }

    public Page media(String galleryId, int offset, int limit, boolean admin) {
        if (offset < 0 || limit < 1 || limit > 200)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Use offset >= 0 and limit between 1 and 200.");
        Path folder = gallery(galleryId, admin);
        var meta = metadata(folder);
        try (var paths = Files.list(folder)) {
            List<Path> files = paths.filter(this::supported).filter(p -> admin || !isHidden(meta, p.getFileName().toString())).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
            List<Media> items = new ArrayList<>();
            for (Path file : files.stream().skip(offset).limit(limit).toList()) {
                String name = file.getFileName().toString();
                String id = encode(name);
                String mime = mime(file);
                items.add(new Media(id, name, mime.startsWith("image/") ? "image" : "video", mime,
                        Files.size(file), Files.getLastModifiedTime(file).toMillis(),
                        (admin ? "/api/gallery-admin/galleries/" : "/api/public/galleries/") + galleryId + "/media/" + id + "/content", isHidden(meta, name)));
            }
            return new Page(items, offset, limit, files.size());
        } catch (IOException e) { throw unavailable(); }
    }

    public Path content(String galleryId, String mediaId) { return content(galleryId, mediaId, false); }

    public Path content(String galleryId, String mediaId, boolean admin) {
        Path folder = gallery(galleryId, admin);
        String name = decode(mediaId);
        if (name.contains("/") || name.contains("\\")) throw missing();
        Path file = folder.resolve(name).normalize();
        if (!folder.equals(file.getParent()) || !supported(file)) throw missing();
        if (!admin && isHidden(metadata(folder), name)) throw missing();
        return file;
    }

    public Media cover(String galleryId) throws IOException {
        Path folder = gallery(galleryId);
        var meta = metadata(folder);
        String name = meta.getProperty("cover");
        if (name == null || isHidden(meta, name)) throw missing();
        Path file = content(galleryId, encode(name));
        return new Media(encode(name), name, mime(file).startsWith("image/") ? "image" : "video",
                mime(file), Files.size(file), Files.getLastModifiedTime(file).toMillis(),
                "/api/public/galleries/" + galleryId + "/media/" + encode(name) + "/content", false);
    }

    private Properties metadata(Path folder) {
        Properties result = new Properties();
        Path file = folder.resolve(".e-gallery.properties");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return result;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw unavailable();
        try (var in = Files.newInputStream(file)) { result.load(in); return result; }
        catch (IOException e) { throw unavailable(); }
    }

    private boolean isHidden(Properties meta, String name) {
        return Boolean.parseBoolean(meta.getProperty("hidden." + encode(name)));
    }

    private void saveMetadata(Path folder, Properties meta) throws IOException {
        Path temp = Files.createTempFile(folder, ".metadata-", ".tmp");
        try {
            try (var out = Files.newOutputStream(temp)) { meta.store(out, "E Gallery settings"); }
            Files.move(temp, folder.resolve(".e-gallery.properties"), StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    private String safeName(String name) {
        if (name == null || name.isBlank() || name.length() > 200 || !name.equals(name.trim())
                || hiddenFolder(name) || name.contains("/") || name.contains("\\")
                || name.chars().anyMatch(c -> c < 32 || "<>:\"|?*".indexOf(c) >= 0) || name.endsWith("."))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NAME", "Use a normal file or folder name without slashes or reserved characters.");
        return name;
    }

    public synchronized void createFolder(String name) throws IOException {
        requireStorage();
        Files.createDirectory(root.resolve(safeName(name)));
    }

    public synchronized String renameFolder(String id, String name) throws IOException {
        Path folder = gallery(id, true);
        name = safeName(name);
        if (!folder.getFileName().toString().equals(name)) Files.move(folder, root.resolve(name));
        return encode(name);
    }

    public synchronized void hideFolder(String id, boolean hidden) throws IOException {
        Path folder = gallery(id, true);
        var meta = metadata(folder); meta.setProperty("hidden", Boolean.toString(hidden)); saveMetadata(folder, meta);
    }

    public synchronized void setCover(String id, String mediaId) throws IOException {
        Path folder = gallery(id, true);
        var meta = metadata(folder);
        if (mediaId == null || mediaId.isBlank()) meta.remove("cover");
        else {
            Path file = content(id, mediaId, true);
            if (!mime(file).startsWith("image/") || isHidden(meta, file.getFileName().toString()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COVER", "Choose a visible photo for the cover.");
            meta.setProperty("cover", file.getFileName().toString());
        }
        saveMetadata(folder, meta);
    }

    public synchronized void hideMedia(String id, String mediaId, boolean hidden) throws IOException {
        Path file = content(id, mediaId, true);
        var meta = metadata(file.getParent());
        meta.setProperty("hidden." + mediaId, Boolean.toString(hidden));
        if (hidden && file.getFileName().toString().equals(meta.getProperty("cover"))) meta.remove("cover");
        saveMetadata(file.getParent(), meta);
    }

    public synchronized void renameMedia(String id, String mediaId, String name) throws IOException {
        Path file = content(id, mediaId, true);
        name = safeName(name);
        Path target = file.getParent().resolve(name);
        if (!Objects.equals(mime(file), mime(target)))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXTENSION", "Keep the same file format when renaming.");
        if (target.equals(file)) return;
        var meta = metadata(file.getParent());
        String hidden = meta.getProperty("hidden." + mediaId);
        Files.move(file, target);
        meta.remove("hidden." + mediaId);
        if (hidden != null) meta.setProperty("hidden." + encode(name), hidden);
        if (file.getFileName().toString().equals(meta.getProperty("cover"))) meta.setProperty("cover", name);
        saveMetadata(target.getParent(), meta);
    }

    private void trash(Path source) throws IOException {
        requireStorage();
        Path trash = root.resolve(".e-gallery-trash");
        if (Files.exists(trash, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(trash, LinkOption.NOFOLLOW_LINKS)) throw unavailable();
        Files.createDirectories(trash);
        Files.move(source, trash.resolve(UUID.randomUUID() + "-" + source.getFileName()));
    }

    public synchronized void deleteFolder(String id) throws IOException { trash(gallery(id, true)); }

    public synchronized void deleteMedia(String id, String mediaId) throws IOException {
        Path file = content(id, mediaId, true);
        var meta = metadata(file.getParent());
        trash(file);
        meta.remove("hidden." + mediaId);
        if (file.getFileName().toString().equals(meta.getProperty("cover"))) meta.remove("cover");
        saveMetadata(file.getParent(), meta);
    }

    public synchronized void upload(String id, org.springframework.web.multipart.MultipartFile upload) throws IOException {
        Path folder = gallery(id, true);
        String name = safeName(upload.getOriginalFilename());
        Path target = folder.resolve(name);
        if (mime(target) == null || upload.isEmpty() || upload.getSize() > 512L * 1024 * 1024)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD", "Upload a supported photo or video up to 512 MB.");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(name);
        Path temp = Files.createTempFile(folder, ".upload-", ".tmp");
        try {
            upload.transferTo(temp);
            // Exclude text/HTML disguised as media; original filenames remain intact.
            byte[] header;
            try (var input = Files.newInputStream(temp)) { header = input.readNBytes(16); }
            String type = mime(target);
            boolean valid = header.length >= 12 && (
                    (type.equals("image/jpeg") && (header[0] & 255) == 255 && (header[1] & 255) == 216) ||
                    (type.equals("image/png") && (header[0] & 255) == 137 && header[1] == 80 && header[2] == 78 && header[3] == 71) ||
                    (type.equals("image/gif") && new String(header, 0, 3, StandardCharsets.US_ASCII).equals("GIF")) ||
                    (type.equals("image/webp") && new String(header, 0, 4, StandardCharsets.US_ASCII).equals("RIFF") && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) ||
                    (Set.of("image/avif", "image/heic", "image/heif", "video/mp4", "video/quicktime").contains(type)
                        && Set.of("ftyp", "moov", "mdat", "wide").contains(new String(header, 4, 4, StandardCharsets.US_ASCII))) ||
                    (Set.of("video/webm", "video/x-matroska").contains(type) && (header[0] & 255) == 26 && (header[1] & 255) == 69 && (header[2] & 255) == 223 && (header[3] & 255) == 163));
            if (!valid) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA", "This file does not match its media format.");
            Files.move(temp, target);
        } finally { Files.deleteIfExists(temp); }
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
