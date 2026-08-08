package com.eesara.drive.user.controller;

import com.eesara.drive.auth.dto.UserResponse;
import com.eesara.drive.security.service.CurrentUserService;
import com.eesara.drive.user.dto.ChangePasswordRequest;
import com.eesara.drive.user.dto.UpdateProfileRequest;
import com.eesara.drive.user.entity.User;
import com.eesara.drive.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<UserResponse> getProfile() { return ResponseEntity.ok(toResponse(currentUserService.getCurrentUser())); }

    @PutMapping
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        User user = currentUserService.getCurrentUser();
        user.setName(request.getName().trim());
        return ResponseEntity.ok(toResponse(userRepository.save(user)));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        User user = currentUserService.getCurrentUser();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) throw new RuntimeException("Current password is incorrect");
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    private UserResponse toResponse(User user) { return UserResponse.builder().uuid(user.getUuid()).name(user.getName()).email(user.getEmail()).role(user.getRole()).storageLimit(user.getStorageLimit()).usedStorage(user.getUsedStorage()).build(); }
}
