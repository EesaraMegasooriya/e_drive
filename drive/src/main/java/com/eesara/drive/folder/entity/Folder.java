package com.eesara.drive.folder.entity;

import com.eesara.drive.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "folders", indexes = {
        @Index(name = "idx_folders_owner_parent_deleted", columnList = "owner_id,parent_id,is_deleted"),
        @Index(name = "idx_folders_parent_name", columnList = "parent_id,name")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String uuid;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "parent")
    @Builder.Default
    private List<Folder> children = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Folder parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 2000)
    private String path;

    @Column(length = 2048)
    private String coverImageUrl;

    @Column(length = 32)
    private String coverIcon;

    @Builder.Default
    private Boolean isDeleted = false;

    @Builder.Default
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {

        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }

        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
