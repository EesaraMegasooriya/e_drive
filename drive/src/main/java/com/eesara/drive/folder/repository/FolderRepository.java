package com.eesara.drive.folder.repository;

import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    Optional<Folder> findByUuid(String uuid);

    Optional<Folder> findByUuidAndOwner(
            String uuid,
            User owner
    );

    List<Folder> findByOwner(User owner);

    List<Folder> findByParent(Folder parent);

    List<Folder> findByParentAndOwner(
            Folder parent,
            User owner
    );

    Optional<Folder> findByParentAndNameAndOwner(
            Folder parent,
            String name,
            User owner
    );

    boolean existsByParentAndNameAndOwner(
            Folder parent,
            String name,
            User owner
    );

    List<Folder> findByPathStartingWith(String path);

    long countByParentAndOwner(
            Folder parent,
            User owner
    );

}