package com.eesara.drive.admin.controller;

import com.eesara.drive.file.repository.DriveFileRepository;
import com.eesara.drive.folder.repository.FolderRepository;
import com.eesara.drive.share.repository.ShareLinkRepository;
import com.eesara.drive.user.entity.User;
import com.eesara.drive.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserRepository users;
    private final DriveFileRepository files;
    private final FolderRepository folders;
    private final ShareLinkRepository shares;

    @GetMapping("/overview")
    public Map<String, Object> overview() { return Map.of("users", users.count(), "files", files.count(), "folders", folders.count(), "shares", shares.count()); }

    @GetMapping("/users")
    public List<Map<String, Object>> listUsers() { return users.findAll().stream().map(this::user).toList(); }

    @GetMapping("/files")
    public List<Map<String, Object>> listFiles() { return files.findAll().stream().map(file -> Map.<String, Object>of("uuid", file.getUuid(), "name", file.getOriginalName(), "owner", file.getOwner().getEmail(), "size", file.getFileSize(), "isPublic", Boolean.TRUE.equals(file.getIsPublic()))).toList(); }

    @PutMapping("/users/{uuid}/active")
    public ResponseEntity<Void> setUserActive(@PathVariable String uuid, @RequestParam boolean active) {
        User user = users.findByUuid(uuid).orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsActive(active); users.save(user); return ResponseEntity.noContent().build();
    }

    private Map<String, Object> user(User user) { return Map.of("uuid", user.getUuid(), "name", user.getName(), "email", user.getEmail(), "role", user.getRole().name(), "active", Boolean.TRUE.equals(user.getIsActive()), "usedStorage", user.getUsedStorage()); }
}
