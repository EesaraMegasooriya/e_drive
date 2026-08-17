package com.eesara.drive.file.service;

import com.eesara.drive.file.repository.DriveFileRepository;
import com.eesara.drive.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileMaintenanceService {
    private final DriveFileRepository files;
    private final StorageService storage;

    /** Hash one pending file at a time so large uploads can finish promptly. */
    @Scheduled(fixedDelayString = "${storage.checksum-delay-ms:10000}")
    public void checksumNextFile() {
        files.findFirstByChecksumIsNullAndDeletedFalseOrderByCreatedAtAsc().ifPresent(file -> {
            try {
                file.setChecksum(storage.checksum(file.getStoragePath()));
                files.save(file);
            } catch (Exception ignored) {
                // A later run retries transient storage failures.
            }
        });
    }
}
