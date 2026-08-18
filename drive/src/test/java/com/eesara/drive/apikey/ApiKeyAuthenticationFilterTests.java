package com.eesara.drive.apikey;
import com.eesara.drive.audit.AuditLogRepository;
import com.eesara.drive.user.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
class ApiKeyAuthenticationFilterTests {
 private ApiKeyRepository repository;private ApiKeyAuthenticationFilter filter;private ApiKeyHasher hasher;private User user;
 @BeforeEach void setup(){repository=mock(ApiKeyRepository.class);hasher=new ApiKeyHasher();ApiKeyRateLimiter limiter=new ApiKeyRateLimiter();ReflectionTestUtils.setField(limiter,"limit",300);filter=new ApiKeyAuthenticationFilter(repository,hasher,limiter,mock(AuditLogRepository.class));ReflectionTestUtils.setField(filter,"enabled",true);ReflectionTestUtils.setField(filter,"prefix","edrive_");user=User.builder().id(1L).uuid("11111111-1111-1111-1111-111111111111").email("owner@example.com").role(Role.USER).isActive(true).build();SecurityContextHolder.clearContext();}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void authenticatesValidKey()throws Exception{String raw="edrive_test_secret";when(repository.findByKeyHash(hasher.hash(raw))).thenReturn(Optional.of(key(raw,true,null)));MockHttpServletRequest request=request(raw);MockHttpServletResponse response=new MockHttpServletResponse();filter.doFilter(request,response,new MockFilterChain());assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isInstanceOf(ApiKeyPrincipal.class);verify(repository).save(any());}
 @Test void rejectsInvalidKey()throws Exception{MockHttpServletResponse response=new MockHttpServletResponse();filter.doFilter(request("edrive_invalid"),response,new MockFilterChain());assertThat(response.getStatus()).isEqualTo(401);assertThat(response.getContentAsString()).contains("INVALID_API_KEY");}
 @Test void rejectsRevokedKey()throws Exception{String raw="edrive_revoked";when(repository.findByKeyHash(hasher.hash(raw))).thenReturn(Optional.of(key(raw,false,null)));MockHttpServletResponse response=new MockHttpServletResponse();filter.doFilter(request(raw),response,new MockFilterChain());assertThat(response.getStatus()).isEqualTo(401);assertThat(response.getContentAsString()).contains("API_KEY_REVOKED");}
 @Test void rejectsExpiredKey()throws Exception{String raw="edrive_expired";when(repository.findByKeyHash(hasher.hash(raw))).thenReturn(Optional.of(key(raw,true,LocalDateTime.now().minusMinutes(1))));MockHttpServletResponse response=new MockHttpServletResponse();filter.doFilter(request(raw),response,new MockFilterChain());assertThat(response.getStatus()).isEqualTo(401);assertThat(response.getContentAsString()).contains("API_KEY_EXPIRED");}
 private MockHttpServletRequest request(String raw){MockHttpServletRequest r=new MockHttpServletRequest("POST","/api/files/upload");r.addHeader("Authorization","Bearer "+raw);return r;}
 private ApiKey key(String raw,boolean active,LocalDateTime expiry){return ApiKey.builder().id(9L).name("test").keyHash(hasher.hash(raw)).keyPrefix("edrive_test").user(user).scopes("files:upload").isActive(active).expiresAt(expiry).build();}
}
