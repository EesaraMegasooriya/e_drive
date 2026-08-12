package com.eesara.drive.config;

import com.eesara.drive.user.entity.Role;
import com.eesara.drive.user.entity.User;
import com.eesara.drive.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminSeedConfig {
    @Bean
    CommandLineRunner seedAdmin(UserRepository users, PasswordEncoder encoder) {
        return args -> {
            User administrator = users.findByEmail("eesara@gmail.com")
                    .orElseGet(() -> User.builder()
                            .name("EDrive Administrator")
                            .email("eesara@gmail.com")
                            .build());

            administrator.setRole(Role.ADMIN);
            administrator.setIsActive(true);
            administrator.setPassword(encoder.encode("password123"));
            users.save(administrator);

            users.findByRole(Role.ADMIN).stream()
                    .filter(user -> !user.getEmail().equalsIgnoreCase("eesara@gmail.com"))
                    .forEach(user -> {
                        user.setRole(Role.USER);
                        users.save(user);
                    });
        };
    }
}
