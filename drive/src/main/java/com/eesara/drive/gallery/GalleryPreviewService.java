package com.eesara.drive.gallery;

import com.eesara.drive.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

@Service
public class GalleryPreviewService {
    private final Path cache;
    private final String ffmpeg;
    private final Semaphore workers = new Semaphore(2, true);

    public GalleryPreviewService(
            @Value("${gallery.preview-cache:${storage.location:storage}/.gallery-previews}") String cache,
            @Value("${gallery.ffmpeg:ffmpeg}") String ffmpeg) {
        this.cache = Path.of(cache);
        this.ffmpeg = ffmpeg;
    }

    public static String version(Path source) throws IOException {
        return Files.size(source) + "-" + Files.getLastModifiedTime(source).toMillis();
    }

    public Path preview(Path source, int edge) {
        boolean acquired = false;
        Path temp = null;
        Process process = null;
        try {
            String identity = source.toAbsolutePath() + ":" + version(source) + ":" + edge + ":v1";
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
            Path target = cache.resolve(hash + ".jpg");
            if (Files.isRegularFile(target)) return target;
            acquired = workers.tryAcquire(45, TimeUnit.SECONDS);
            if (!acquired) throw unavailable();
            if (Files.isRegularFile(target)) return target;
            Files.createDirectories(cache);
            temp = Files.createTempFile(cache, "pending-", ".jpg");
            process = new ProcessBuilder(ffmpeg, "-nostdin", "-v", "error", "-y",
                    "-i", source.toString(), "-map", "0:v:0", "-frames:v", "1",
                    "-vf", "scale=" + edge + ":" + edge + ":force_original_aspect_ratio=decrease",
                    "-threads", "1", "-q:v", "4", temp.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(45, TimeUnit.SECONDS) || process.exitValue() != 0 || Files.size(temp) == 0)
                throw unavailable();
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (java.security.NoSuchAlgorithmException | IOException e) {
            throw unavailable();
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            if (acquired) workers.release();
        }
    }

    // Bound persistent cache age, including previews for removed/replaced originals.
    @Scheduled(cron = "0 10 4 * * *")
    public void cleanCache() throws IOException {
        if (!Files.isDirectory(cache)) return;
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
        try (var paths = Files.list(cache)) {
            paths.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.jpg"))
                .forEach(p -> {
                    try { if (Files.getLastModifiedTime(p).toMillis() < cutoff) Files.deleteIfExists(p); }
                    catch (IOException ignored) { }
                });
        }
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GALLERY_PREVIEW_UNAVAILABLE", "Preview could not be generated.");
    }
}
