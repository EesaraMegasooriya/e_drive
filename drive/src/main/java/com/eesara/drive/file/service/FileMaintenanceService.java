package com.eesara.drive.file.service;

import com.eesara.drive.file.repository.DriveFileRepository;
import com.eesara.drive.storage.StorageService;
import com.eesara.drive.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileMaintenanceService {
    private final DriveFileRepository files;
    private final StorageService storage;
    private final UserRepository users;

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

    /** Repairs records created by older resumable uploads that saved size 0. */
    @Scheduled(fixedDelayString = "${storage.size-repair-delay-ms:60000}", initialDelay = 5000)
    public void repairIncorrectFileSizes() {
        files.findByFileSize(0L).forEach(file -> {
            try {
                long physicalSize = java.nio.file.Files.size(storage.load(file.getStoragePath()));
                if (physicalSize <= 0) return; // A legitimate empty file.
                file.setFileSize(physicalSize);
                files.save(file);
                var owner = file.getOwner();
                owner.setUsedStorage(Math.addExact(owner.getUsedStorage(), physicalSize));
                users.save(owner);
            } catch (Exception ignored) {
                // Missing/transient storage entries are retried by a later run.
            }
        });
    }
}
