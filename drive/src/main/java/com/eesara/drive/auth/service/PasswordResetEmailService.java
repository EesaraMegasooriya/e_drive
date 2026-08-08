package com.eesara.drive.auth.service;

import com.eesara.drive.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class PasswordResetEmailService {

    @Value("${app.resend.api-key:}")
    private String apiKey;

    @Value("${app.resend.from:EDrive <onboarding@resend.dev>}")
    private String from;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public void send(User user, String token) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Password reset email is not configured");
        }

        String resetUrl = frontendUrl.replaceAll("/$", "")
                + "/reset-password?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);

        RestClient.builder().baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("User-Agent", "EDrive password reset")
                .build()
                .post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "from", from,
                        "to", new String[]{user.getEmail()},
                        "subject", "Reset your EDrive password",
                        "html", emailHtml(user.getName(), resetUrl),
                        "text", "Reset your EDrive password: " + resetUrl
                ))
                .retrieve()
                .toBodilessEntity();
    }

    private String emailHtml(String name, String resetUrl) {
        String safeName = name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<p>Hello " + safeName + ",</p>"
                + "<p>Use the link below to reset your EDrive password. It expires in 30 minutes.</p>"
                + "<p><a href=\"" + resetUrl + "\">Reset password</a></p>"
                + "<p>If you did not request this, you can safely ignore this email.</p>";
    }
}
