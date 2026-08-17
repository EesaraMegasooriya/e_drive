package com.eesara.drive.share.repository;

import com.eesara.drive.file.entity.DriveFile;
import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.share.entity.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {

    Optional<ShareLink> findByToken(String token);

    Optional<ShareLink> findByUuid(String uuid);

    List<ShareLink> findByOwnerId(Long ownerId);

    List<ShareLink> findByFile(DriveFile file);

    List<ShareLink> findByFolder(Folder folder);

    Optional<ShareLink> findByFileAndActiveTrue(DriveFile file);

    Optional<ShareLink> findByFolderAndActiveTrue(Folder folder);

    @Modifying
    @Transactional
    void deleteAllByFile(DriveFile file);

    @Modifying
    @Transactional
    void deleteAllByFolder(Folder folder);
}
