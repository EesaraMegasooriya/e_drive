package com.eesara.drive.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.scheduling.annotation.Scheduled;

@Service
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private final StorageProperties properties;

    @Override
    public String save(MultipartFile file) throws IOException {

        String extension = "";

        String original = file.getOriginalFilename();

        if (original != null && original.contains(".")) {
            extension = original.substring(original.lastIndexOf("."));
        }

        String filename = UUID.randomUUID() + extension;

        String folder = filename.substring(0, 2);

        Path directory = Paths.get(properties.getLocation(), folder);

        Files.createDirectories(directory);

        Path destination = directory.resolve(filename).toAbsolutePath().normalize();

        // The servlet container has already streamed large multipart requests
        // to disk. transferTo lets it move that temporary file when possible,
        // avoiding a second multi-gigabyte copy and a long pause at 100%.
        file.transferTo(destination);

        return folder + "/" + filename;
    }

    @Override
    public Path load(String storagePath) {

        return Paths.get(
                properties.getLocation(),
                storagePath
        );
    }

    @Override
    public Resource loadAsResource(String storagePath) {

        try {

            Path file = load(storagePath);

            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            }

            throw new RuntimeException("File not found");

        } catch (MalformedURLException e) {

            throw new RuntimeException("Unable to load file", e);

        }

    }

    @Override
    public void delete(String storagePath) throws IOException {

        Files.deleteIfExists(
                load(storagePath)
        );

    }

    @Override
    public String checksum(String storagePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream input = new DigestInputStream(
                    Files.newInputStream(load(storagePath)), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    @Override
    public String copy(String storagePath) throws IOException {
        Path source = load(storagePath);
        String originalStoredName = source.getFileName().toString();
        String extension = originalStoredName.contains(".")
                ? originalStoredName.substring(originalStoredName.lastIndexOf('.'))
                : "";
        String filename = UUID.randomUUID() + extension;
        String folder = filename.substring(0, 2);
        Path directory = Paths.get(properties.getLocation(), folder);
        Files.createDirectories(directory);
        Files.copy(source, directory.resolve(filename), StandardCopyOption.COPY_ATTRIBUTES);
        return folder + "/" + filename;
    }

    @Override
    public StorageStats stats() throws IOException {
        Path root = Paths.get(properties.getLocation()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        FileStore fileStore = Files.getFileStore(root);
        return new StorageStats(fileStore.getTotalSpace(), fileStore.getUsableSpace());
    }

    @Override
    public void deleteUserTemporaryData(String userUuid) throws IOException {
        UUID.fromString(userUuid); // Reject unexpected path input.
        Path chunksRoot = Paths.get(properties.getLocation(), ".tmp", "chunks")
                .toAbsolutePath().normalize();
        Path userDirectory = chunksRoot.resolve(userUuid).normalize();
        if (!userDirectory.startsWith(chunksRoot) || !Files.exists(userDirectory)) return;
        try (Stream<Path> paths = Files.walk(userDirectory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Scheduled(cron = "${storage.cleanup-cron:0 30 3 * * *}")
    public void cleanupTemporaryFiles() throws IOException {
        Path temporaryDirectory = Paths.get(properties.getLocation(), ".tmp");
        if (!Files.isDirectory(temporaryDirectory)) return;
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        try (Stream<Path> paths = Files.walk(temporaryDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
                        } catch (IOException ignored) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                    });
        }
    }

}
