package com.eesara.drive.share.service;

import com.eesara.drive.file.dto.FileDownloadResponse;
import com.eesara.drive.share.dto.StreamPlaybackResponse;
import com.eesara.drive.share.dto.StreamPlaybackResponse.SubtitleTrackResponse;
import com.eesara.drive.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class VideoStreamService {
    private final StorageProperties properties;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public StreamPlaybackResponse prepare(String token, String fileUuid, FileDownloadResponse source) {
        Path output = outputDirectory(fileUuid);
        Path playlist = output.resolve("index.m3u8");
        synchronized (locks.computeIfAbsent(fileUuid, ignored -> new Object())) {
            if (!Files.isRegularFile(playlist)) generate(source, output);
        }
        String base = "/api/public/folders/" + token + "/streams/" + fileUuid;
        List<SubtitleTrackResponse> subtitles;
        try (var paths = Files.list(output)) {
            subtitles = paths.filter(path -> path.getFileName().toString().matches("subtitle-\\d+\\.vtt"))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> {
                        String name = path.getFileName().toString();
                        String number = name.substring(9, name.length() - 4);
                        return new SubtitleTrackResponse("Subtitle " + number, "und", base + "/" + name);
                    }).toList();
        } catch (IOException exception) {
            subtitles = List.of();
        }
        return new StreamPlaybackResponse(base + "/index.m3u8", subtitles);
    }

    public Resource resource(String fileUuid, String name) {
        if (!name.matches("(?:index\\.m3u8|segment-\\d{5}\\.ts|subtitle-\\d+\\.vtt)"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stream resource");
        Path file = outputDirectory(fileUuid).resolve(name).normalize();
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stream resource not found");
        return new FileSystemResource(file);
    }

    private Path outputDirectory(String fileUuid) {
        if (!fileUuid.matches("[0-9a-fA-F-]{36}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file id");
        return Path.of(properties.getLocation(), ".streams", fileUuid).toAbsolutePath().normalize();
    }

    private void generate(FileDownloadResponse source, Path output) {
        try {
            Files.createDirectories(output);
            Path input = source.getResource().getFile().toPath();
            Process video = new ProcessBuilder("ffmpeg", "-y", "-i", input.toString(),
                    "-map", "0:v:0", "-map", "0:a:0?", "-c:v", "libx264", "-preset", "veryfast",
                    "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-sn",
                    "-hls_time", "6", "-hls_playlist_type", "vod",
                    "-hls_segment_filename", output.resolve("segment-%05d.ts").toString(),
                    output.resolve("index.m3u8").toString())
                    .redirectError(output.resolve("ffmpeg.log").toFile()).start();
            if (video.waitFor() != 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "FFmpeg could not convert this video");

            Process probe = new ProcessBuilder("ffprobe", "-v", "error", "-select_streams", "s",
                    "-show_entries", "stream=index", "-of", "csv=p=0", input.toString())
                    .redirectErrorStream(true).start();
            List<String> subtitleIndexes;
            try (var reader = probe.inputReader()) { subtitleIndexes = reader.lines().map(String::trim).filter(s -> s.matches("\\d+")).toList(); }
            probe.waitFor();
            for (String index : subtitleIndexes) {
                Path target = output.resolve("subtitle-" + index + ".vtt");
                Process subtitles = new ProcessBuilder("ffmpeg", "-y", "-i", input.toString(),
                        "-map", "0:" + index, "-c:s", "webvtt", target.toString())
                        .redirectError(output.resolve("subtitles-" + index + ".log").toFile()).start();
                if (subtitles.waitFor() != 0) Files.deleteIfExists(target);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Video processing was interrupted");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Video processing is unavailable; ensure FFmpeg is installed", exception);
        }
    }
}
