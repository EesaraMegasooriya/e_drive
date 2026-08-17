package com.eesara.drive.auth.service;

import com.eesara.drive.auth.dto.LoginRequest;
import com.eesara.drive.auth.dto.LoginResponse;
import com.eesara.drive.auth.dto.RegisterRequest;
import com.eesara.drive.auth.dto.UserResponse;
import com.eesara.drive.auth.dto.ForgotPasswordRequest;
import com.eesara.drive.auth.dto.ResetPasswordRequest;
import com.eesara.drive.security.jwt.JwtService;
import com.eesara.drive.user.entity.Role;
import com.eesara.drive.user.entity.User;
import com.eesara.drive.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import com.eesara.drive.common.ApiException;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PasswordResetEmailService passwordResetEmailService;

    @Override
    public void register(RegisterRequest request) {

        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS",
                    "An account already exists with this email address.");
        }

        User user = User.builder()
                .name(request.getName())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {

        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "EMAIL_NOT_FOUND",
                        "No account was found with this email address."));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED",
                    "This account has been suspended. Contact an administrator.");
        }

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (BadCredentialsException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INCORRECT_PASSWORD",
                    "The password you entered is incorrect.");
        }

        String token = jwtService.generateToken(user.getEmail());

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .user(
                        UserResponse.builder()
                                .uuid(user.getUuid())
                                .name(user.getName())
                                .email(user.getEmail())
                                .role(user.getRole())
                                .storageLimit(user.getStorageLimit())
                                .usedStorage(user.getUsedStorage())
                                .build()
                )
                .build();
    }

    @Override
    public void requestPasswordReset(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .ifPresent(user -> {
                    String token = generateResetToken();
                    user.setPasswordResetTokenHash(hashToken(token));
                    user.setPasswordResetExpiresAt(LocalDateTime.now().plusMinutes(30));
                    userRepository.save(user);
                    passwordResetEmailService.send(user, token);
                });
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetTokenHash(hashToken(request.getToken()))
                .orElseThrow(() -> new ApiException(HttpStatus.GONE, "INVALID_RESET_LINK",
                        "This reset link is invalid or has expired."));

        if (user.getPasswordResetExpiresAt() == null
                || user.getPasswordResetExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.GONE, "INVALID_RESET_LINK",
                    "This reset link is invalid or has expired.");
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
        userRepository.save(user);
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
