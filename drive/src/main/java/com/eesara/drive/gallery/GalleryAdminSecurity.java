package com.eesara.drive.gallery;

import com.eesara.drive.common.ApiException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.security.*;
import java.util.*;

@Configuration
public class GalleryAdminSecurity {
    @Bean @Order(1)
    SecurityFilterChain galleryAdminChain(HttpSecurity http, Sessions sessions) throws Exception {
        return http.securityMatcher("/api/gallery-admin/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/gallery-admin/login").permitAll()
                        .anyRequest().hasRole("GALLERY_ADMIN"))
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401); res.setContentType("application/json");
                    res.getWriter().write("{\"message\":\"Please sign in to Gallery Admin.\"}");
                }))
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                            throws ServletException, IOException {
                        if (sessions.valid(req.getHeader("X-Gallery-Token"))) {
                            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                                    "Gallery Admin", null, List.of(new SimpleGrantedAuthority("ROLE_GALLERY_ADMIN"))));
                        }
                        res.setHeader("Cache-Control", "no-store");
                        chain.doFilter(req, res);
                    }
                }, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Service
    public static class Sessions {
        private static final String DEFAULT_HASH = "e99eefeba3444ea9b676cba94e73bad4:c009ef00ea535e4906e280e88351c61799d4f73a2b74ab0ca9673fd3bbf98571";
        private final String username;
        private final String passwordHash;
        private final Map<String, Long> tokens = new HashMap<>();
        private final Deque<Long> attempts = new ArrayDeque<>();

        public Sessions(@Value("${gallery.admin.username:GallaryAdmin}") String username,
                        @Value("${gallery.admin.password-hash:" + DEFAULT_HASH + "}") String passwordHash) {
            this.username = username;
            this.passwordHash = passwordHash;
        }
        public synchronized String login(String name, String password) {
            long now = System.currentTimeMillis();
            while (!attempts.isEmpty() && attempts.peekFirst() < now - 300_000) attempts.removeFirst();
            if (attempts.size() >= 20) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMIT", "Too many sign-in attempts. Try again in five minutes.");
            attempts.addLast(now);
            boolean passwordMatches = false;
            if (password != null && password.length() <= 256) {
                try {
                    String[] parts = passwordHash.split(":");
                    var spec = new PBEKeySpec(password.toCharArray(), HexFormat.of().parseHex(parts[0]), 210000, 256);
                    try {
                        byte[] computed = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
                        passwordMatches = MessageDigest.isEqual(computed, HexFormat.of().parseHex(parts[1]));
                    } finally { spec.clearPassword(); }
                } catch (GeneralSecurityException | IllegalArgumentException e) {
                    throw new IllegalStateException("Invalid gallery admin password configuration", e);
                }
            }
            if (!username.equals(name) || !passwordMatches)
                throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "Incorrect username or password.");
            tokens.values().removeIf(expiry -> expiry <= now);
            if (tokens.size() >= 32) tokens.clear();
            byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            tokens.put(token, now + 8 * 60 * 60 * 1000L);
            return token;
        }
        public synchronized boolean valid(String token) {
            if (token == null) return false;
            Long expiry = tokens.get(token);
            if (expiry == null) return false;
            if (expiry <= System.currentTimeMillis()) { tokens.remove(token); return false; }
            return true;
        }
        public synchronized void logout(String token) { tokens.remove(token); }
    }
}
