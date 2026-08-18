package com.eesara.drive.apikey;

import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "api_keys", indexes = @Index(name = "idx_api_keys_hash", columnList = "key_hash", unique = true))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ApiKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 150) private String name;
    @Column(name = "key_hash", nullable = false, unique = true, length = 64) private String keyHash;
    @Column(name = "key_prefix", nullable = false, length = 40) private String keyPrefix;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false, length = 500) private String scopes;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "folder_id") private Folder folder;
    @Builder.Default @Column(name = "is_active", nullable = false) private Boolean isActive = true;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    @Column(updatable = false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist void create() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
    public Set<String> scopeSet() { return Arrays.stream(scopes.split(",")).filter(s -> !s.isBlank()).collect(Collectors.toUnmodifiableSet()); }
}
