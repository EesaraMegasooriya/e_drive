package com.eesara.drive.file.repository;

import com.eesara.drive.file.entity.DriveFile;
import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DriveFileRepository extends JpaRepository<DriveFile, Long> {

    Optional<DriveFile> findByUuid(String uuid);

    List<DriveFile> findByOwner(User owner);

    List<DriveFile> findByFolder(Folder folder);

    List<DriveFile> findByFolderAndOwner(
            Folder folder,
            User owner
    );

    List<DriveFile> findByFolderIsNullAndOwner(
            User owner
    );

    boolean existsByFolderAndOriginalNameAndOwner(
            Folder folder,
            String originalName,
            User owner
    );

    long countByFolderAndOwner(
            Folder folder,
            User owner
    );

}