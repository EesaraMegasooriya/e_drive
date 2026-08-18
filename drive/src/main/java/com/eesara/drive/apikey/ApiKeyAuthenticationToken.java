package com.eesara.drive.apikey;
import org.springframework.security.authentication.AbstractAuthenticationToken;
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {
    private final ApiKeyPrincipal principal;
    public ApiKeyAuthenticationToken(ApiKeyPrincipal principal) { super(principal.authorities()); this.principal = principal; setAuthenticated(true); }
    @Override public Object getCredentials() { return ""; }
    @Override public ApiKeyPrincipal getPrincipal() { return principal; }
}
