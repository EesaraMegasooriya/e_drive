package com.eesara.drive.gallery;

import com.eesara.drive.common.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.HexFormat;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = GalleryAdminSecurityTests.Config.class)
class GalleryAdminSecurityTests {
    @Configuration @EnableWebMvc @EnableWebSecurity @Import(GalleryAdminSecurity.class)
    static class Config {
        @Bean GalleryAdminSecurity.Sessions sessions() throws Exception {
            byte[] salt = new byte[16];
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec("test-password".toCharArray(), salt, 210000, 256)).getEncoded();
            return new GalleryAdminSecurity.Sessions("test-admin", HexFormat.of().formatHex(salt) + ":" + HexFormat.of().formatHex(hash));
        }
        @Bean GalleryService galleries() { return mock(GalleryService.class); }
        @Bean GalleryPreviewService previews() { return mock(GalleryPreviewService.class); }
        @Bean GalleryAdminController controller(GalleryService galleries, GalleryPreviewService previews, GalleryAdminSecurity.Sessions sessions) { return new GalleryAdminController(galleries, previews, sessions); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
    }
    @Autowired WebApplicationContext context;
    @Autowired GalleryAdminSecurity.Sessions sessions;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @Test void protectsReadsAndMutationsAndRevokesLogoutToken() throws Exception {
        mvc.perform(get("/api/gallery-admin/galleries")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/gallery-admin/galleries/abc")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/gallery-admin/session").header("X-Gallery-Token", "forged")).andExpect(status().isUnauthorized());
        String token = sessions.login("test-admin", "test-password");
        mvc.perform(get("/api/gallery-admin/session").header("X-Gallery-Token", token)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/api/gallery-admin/logout").header("X-Gallery-Token", token)).andExpect(status().isOk());
        mvc.perform(get("/api/gallery-admin/session").header("X-Gallery-Token", token)).andExpect(status().isUnauthorized());
    }
    @Test void loginWorksWithoutPublicWriteAccess() throws Exception {
        mvc.perform(post("/api/gallery-admin/login").contentType("application/json").content("{\"username\":\"test-admin\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/gallery-admin/login").contentType("application/json").content("{\"username\":\"test-admin\",\"password\":\"test-password\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(put("/api/gallery-admin/galleries/abc/visibility").contentType("application/json").content("{\"hidden\":false}")).andExpect(status().isUnauthorized());
    }
    @Test void rateLimitsFailedLogins() {
        var isolated = new GalleryAdminSecurity.Sessions("test", "00:00");
        for (int i = 0; i < 20; i++) assertThatThrownBy(() -> isolated.login("bad", null)).isInstanceOf(com.eesara.drive.common.ApiException.class);
        assertThatThrownBy(() -> isolated.login("bad", null)).isInstanceOfSatisfying(com.eesara.drive.common.ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(429));
    }
}
