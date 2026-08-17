package com.eesara.drive.admin.service;

import com.eesara.drive.file.entity.DriveFile;
import com.eesara.drive.file.repository.DriveFileRepository;
import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.folder.repository.FolderRepository;
import com.eesara.drive.share.repository.ShareLinkRepository;
import com.eesara.drive.storage.StorageService;
import com.eesara.drive.user.entity.Role;
import com.eesara.drive.user.entity.User;
import com.eesara.drive.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final DriveFileRepository driveFileRepository;
    private final FolderRepository folderRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final StorageService storageService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void resetPassword(String userUuid, String password) {
        User user = findUser(userUuid);
        preventAdminModification(user);

        user.setPassword(passwordEncoder.encode(password));
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(String userUuid) {
        User user = findUser(userUuid);
        preventAdminModification(user);

        List<DriveFile> userFiles = driveFileRepository.findByOwner(user);

        try {
            storageService.deleteUserTemporaryData(user.getUuid());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not remove temporary uploads for this user", exception);
        }

        // Share records reference files and folders, so remove them before their owners.
        shareLinkRepository.deleteAll(shareLinkRepository.findByOwnerId(user.getId()));

        for (DriveFile file : userFiles) {
            try {
                storageService.delete(file.getStoragePath());
            } catch (IOException exception) {
                throw new IllegalStateException("Could not remove stored file " + file.getOriginalName(), exception);
            }
        }
        driveFileRepository.deleteAll(userFiles);

        folderRepository.findByOwner(user).stream()
                .sorted(Comparator.comparingInt((Folder folder) -> folder.getPath().length()).reversed())
                .forEach(folderRepository::delete);

        userRepository.delete(user);
    }

    private User findUser(String userUuid) {
        return userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private void preventAdminModification(User user) {
        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("The administrator account cannot be modified here");
        }
    }
}
