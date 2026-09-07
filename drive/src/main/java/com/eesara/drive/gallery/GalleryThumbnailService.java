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

    public Path thumbnail(Path source) {
        boolean acquired = false;
        Path temporary = null;
        Process process = null;
        try {
            String version = source.toAbsolutePath() + ":" + Files.size(source) + ":" + Files.getLastModifiedTime(source) + ":v1";
            String key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(version.getBytes(StandardCharsets.UTF_8)));
            Path target = cache.resolve(key + ".jpg");
            if (Files.isRegularFile(target)) return target;
            acquired = workers.tryAcquire(2, TimeUnit.SECONDS);
            if (!acquired) throw unavailable();
            if (Files.isRegularFile(target)) return target;
            Files.createDirectories(cache);
            temporary = Files.createTempFile(cache, "thumbnail-", ".jpg");
            process = new ProcessBuilder(executable, "-nostdin", "-v", "error", "-y", "-i", source.toString(),
                    "-map", "0:v:0", "-frames:v", "1", "-vf", "scale=640:640:force_original_aspect_ratio=decrease",
                    "-threads", "1", "-q:v", "4", temporary.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0 || Files.size(temporary) == 0)
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
            if (acquired) workers.release();
        }
    }
    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "THUMBNAIL_UNAVAILABLE", "Preview is unavailable. Please try again.");
    }
}
