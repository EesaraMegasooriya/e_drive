package com.eesara.drive.apikey;

import com.eesara.drive.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.Set;

@Getter
public class ApiKeyPrincipal {
    private final Long apiKeyId;
    private final User user;
    private final Set<String> scopes;
    private final String folderUuid;
    public ApiKeyPrincipal(ApiKey key) {
        apiKeyId = key.getId(); user = key.getUser(); scopes = key.scopeSet();
        folderUuid = key.getFolder() == null ? null : key.getFolder().getUuid();
    }
    public String getName() { return user.getEmail(); }
    public List<SimpleGrantedAuthority> authorities() { return List.of(new SimpleGrantedAuthority("ROLE_SERVICE")); }
}
