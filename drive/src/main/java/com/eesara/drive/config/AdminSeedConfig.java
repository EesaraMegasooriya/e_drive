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
            if (!users.existsByEmail("emadmin@gmail.com")) {
                users.save(User.builder().name("EDrive Administrator").email("emadmin@gmail.com")
                        .password(encoder.encode("eesara@12238")).role(Role.ADMIN).build());
            }
        };
    }
}
