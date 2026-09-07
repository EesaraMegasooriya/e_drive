package com.eesara.drive.gallery;

import com.eesara.drive.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

@Service
public class GalleryThumbnailService {
    private final Path cache;
    private final String executable;
    private final Semaphore workers = new Semaphore(2);

    public GalleryThumbnailService(@Value("${gallery.thumbnail-cache:storage/.gallery-thumbnails}") String cache,
                                   @Value("${gallery.ffmpeg:ffmpeg}") String executable) {
        this.cache = Path.of(cache).toAbsolutePath().normalize();
        this.executable = executable;
    }

    public Path thumbnail(Path source) { return convert(source, false); }

    public Path browserMedia(Path source) { return convert(source, true); }

    private Path convert(Path source, boolean full) {
        boolean acquired = false;
        Path temporary = null;
        Process process = null;
        Path decodedDirectory = null;
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean heif = name.endsWith(".heic") || name.endsWith(".heif");
        boolean video = full && !heif;
        String extension = video ? ".mp4" : ".jpg";
        try {
            String version = source.toAbsolutePath() + ":" + Files.size(source) + ":" + Files.getLastModifiedTime(source) + ":v2:" + full;
            String key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(version.getBytes(StandardCharsets.UTF_8)));
            Path target = cache.resolve(key + extension);
            if (Files.isRegularFile(target)) return target;
            acquired = workers.tryAcquire(2, TimeUnit.SECONDS);
            if (!acquired) throw unavailable();
            if (Files.isRegularFile(target)) return target;
            Files.createDirectories(cache);
            temporary = Files.createTempFile(cache, "preview-", extension);
            Path input = source;
            if (heif) {
                decodedDirectory = Files.createTempDirectory(cache, "heif-");
                input = decodedDirectory.resolve("decoded.jpg");
                process = new ProcessBuilder("heif-convert", source.toString(), input.toString())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0 || !Files.isRegularFile(input))
                    throw unavailable();
            }
            List<String> command = new java.util.ArrayList<>(List.of(executable, "-nostdin", "-v", "error", "-y", "-i", input.toString(), "-map", "0:v:0"));
            if (video) {
                command.addAll(List.of("-map", "0:a:0?", "-c:v", "libx264", "-preset", "veryfast",
                        "-crf", "23", "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2", "-pix_fmt", "yuv420p",
                        "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart"));
            } else {
                String size = full ? "2560:2560" : "640:640";
                command.addAll(List.of("-frames:v", "1", "-vf", "scale=" + size + ":force_original_aspect_ratio=decrease", "-q:v", "3"));
            }
            command.addAll(List.of("-threads", "1", temporary.toString()));
            process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(video ? 600 : 60, TimeUnit.SECONDS) || process.exitValue() != 0 || Files.size(temporary) == 0)
                throw unavailable();
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException | NoSuchAlgorithmException e) {
            throw unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            if (decodedDirectory != null) {
                try (var paths = Files.walk(decodedDirectory)) {
                    for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                } catch (IOException ignored) { }
            }
            if (acquired) workers.release();
        }
    }
    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "THUMBNAIL_UNAVAILABLE", "Preview is unavailable. Please try again.");
    }
}
