package com.eesara.drive.file.entity;

import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.share.entity.ShareLink;
import com.eesara.drive.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "files", indexes = {
        @Index(name = "idx_files_owner_folder_deleted", columnList = "owner_id,folder_id,deleted"),
        @Index(name = "idx_files_folder_name", columnList = "folder_id,original_name")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String storedName;

    @Column(nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private String extension;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private String storagePath;

    private String thumbnailPath;

    private String checksum;

    @Builder.Default
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

    @Builder.Default
    private Boolean deleted = false;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ADD THIS
    @Builder.Default
    @OneToMany(
            mappedBy = "file",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ShareLink> shareLinks = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        uuid = UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
