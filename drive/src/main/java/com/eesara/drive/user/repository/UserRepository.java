package com.eesara.drive.user.repository;

import com.eesara.drive.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUuid(String uuid);

    Optional<User> findByPasswordResetTokenHash(String passwordResetTokenHash);

    boolean existsByEmail(String email);

}
