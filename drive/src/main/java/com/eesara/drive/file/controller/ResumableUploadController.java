package com.eesara.drive.file.controller;

import com.eesara.drive.file.dto.UploadFileResponse;
import com.eesara.drive.file.service.DriveFileService;
import com.eesara.drive.security.service.CurrentUserService;
import com.eesara.drive.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files/upload/resumable")
@RequiredArgsConstructor
public class ResumableUploadController {

    private static final long CHUNK_SIZE = 8L * 1024 * 1024;
    private final DriveFileService driveFileService;
    private final CurrentUserService currentUserService;
    private final StorageProperties storageProperties;

    @Value("${app.upload.max-file-size-bytes:16106127360}")
    private long maximumFileSize;

    @PostMapping("/init")
    public Map<String, Object> initialize(@RequestParam(required = false) String uploadId,
                                          @RequestParam long size) throws IOException {
        var owner = currentUserService.getCurrentUser();
        if (size < 0 || size > maximumFileSize) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 15 GB limit");
        }
        if (size > Math.max(0L, owner.getStorageLimit() - owner.getUsedStorage())) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Storage quota exceeded");
        }
        String id = validId(uploadId);
        Path part = partPath(id);
        Files.createDirectories(part.getParent());
        long existing = Files.exists(part) ? Files.size(part) : 0L;
        long required = Math.max(0L, size - existing);
        if (Files.getFileStore(part.getParent()).getUsableSpace() < required) {
            throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "Not enough server storage space");
        }
        return Map.of("uploadId", id, "offset", existing,
                "chunkSize", CHUNK_SIZE);
    }

    @RequestMapping(value = "/{uploadId}", method = {RequestMethod.POST, RequestMethod.PUT})
    public Map<String, Long> append(@PathVariable String uploadId,
                                    @RequestParam long offset,
                                    @RequestParam("chunk") MultipartFile chunk) throws IOException {
        Path part = partPath(validId(uploadId));
        Files.createDirectories(part.getParent());
        synchronized (part.toString().intern()) {
            long currentSize = Files.exists(part) ? Files.size(part) : 0L;
            if (chunk.getSize() <= 0 || chunk.getSize() > CHUNK_SIZE) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Invalid chunk size");
            }
            var owner = currentUserService.getCurrentUser();
            long remainingQuota = Math.max(0L, owner.getStorageLimit() - owner.getUsedStorage());
            if (currentSize + chunk.getSize() > remainingQuota) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Storage quota exceeded");
            }
            if (currentSize + chunk.getSize() > maximumFileSize) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 15 GB limit");
            }
            if (Files.getFileStore(part.getParent()).getUsableSpace() < chunk.getSize()) {
                throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "Not enough server storage space");
            }
            if (offset != currentSize) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Upload offset mismatch; resume from " + currentSize);
            }
            try (FileChannel output = FileChannel.open(part, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 InputStream input = chunk.getInputStream()) {
                var source = java.nio.channels.Channels.newChannel(input);
                long written = 0;
                while (written < chunk.getSize()) {
                    long count = output.transferFrom(source, offset + written, chunk.getSize() - written);
                    if (count <= 0) break;
                    written += count;
                }
                if (written != chunk.getSize()) {
                    output.truncate(offset);
                    throw new IOException("Incomplete chunk write");
                }
            }
            return Map.of("offset", Files.size(part));
        }
    }

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<UploadFileResponse> complete(@PathVariable String uploadId,
                                                       @RequestParam String name,
                                                       @RequestParam String mimeType,
                                                       @RequestParam long size,
                                                       @RequestParam(required = false) String folderUuid) throws IOException {
        Path part = partPath(validId(uploadId));
        if (size > maximumFileSize) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 15 GB limit");
        }
        if (!Files.exists(part) || Files.size(part) != size) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload is incomplete");
        }
        UploadFileResponse response = driveFileService.upload(
                new PathMultipartFile(name, mimeType, part), folderUuid);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{uploadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String uploadId) throws IOException {
        Files.deleteIfExists(partPath(validId(uploadId)));
    }

    private Path partPath(String uploadId) {
        String ownerUuid = currentUserService.getCurrentUser().getUuid();
        return Path.of(storageProperties.getLocation(), ".tmp", "chunks", ownerUuid, uploadId + ".part")
                .toAbsolutePath().normalize();
    }

    private String validId(String uploadId) {
        if (uploadId == null || uploadId.isBlank()) return UUID.randomUUID().toString();
        try { return UUID.fromString(uploadId).toString(); }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid upload id");
        }
    }

    private static final class PathMultipartFile implements MultipartFile {
        private final String name;
        private final String contentType;
        private final Path path;

        private PathMultipartFile(String name, String contentType, Path path) {
            this.name = name;
            this.contentType = contentType;
            this.path = path;
        }
        public String getName() { return "file"; }
        public String getOriginalFilename() { return name; }
        public String getContentType() { return contentType; }
        public boolean isEmpty() { return getSize() == 0; }
        public long getSize() { try { return Files.size(path); } catch (IOException e) { return 0; } }
        public byte[] getBytes() throws IOException { return Files.readAllBytes(path); }
        public InputStream getInputStream() throws IOException { return Files.newInputStream(path); }
        public void transferTo(java.io.File destination) throws IOException { transferTo(destination.toPath()); }
        public void transferTo(Path destination) throws IOException {
            Files.move(path, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
