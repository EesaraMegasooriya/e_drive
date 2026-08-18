package com.eesara.drive.apikey;
import com.eesara.drive.audit.AuditLog;
import com.eesara.drive.audit.AuditLogRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.*;

@Component @RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
 private final ApiKeyRepository repository; private final ApiKeyHasher hasher; private final ApiKeyRateLimiter limiter;
 private final AuditLogRepository audits;
 @Value("${service.api-keys.enabled:true}") private boolean enabled;
 @Value("${service.api-key.prefix:edrive_}") private String prefix;
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  String header=request.getHeader("Authorization");
  if(header==null||!header.startsWith("Bearer ")||!header.substring(7).startsWith(prefix)){chain.doFilter(request,response);return;}
  if(!enabled){reject(response,HttpStatus.UNAUTHORIZED,"API_KEY_DISABLED","Service API keys are disabled.");return;}
  String raw=header.substring(7);
  ApiKey key=repository.findByKeyHash(hasher.hash(raw)).orElse(null);
  if(key==null){audit(null,null,"INVALID_API_KEY",request,"REJECTED");reject(response,HttpStatus.UNAUTHORIZED,"INVALID_API_KEY","The API key is invalid.");return;}
  if(!Boolean.TRUE.equals(key.getIsActive())){audit(key.getId(),key.getUser().getId(),"REVOKED_API_KEY",request,"REJECTED");reject(response,HttpStatus.UNAUTHORIZED,"API_KEY_REVOKED","The API key has been revoked.");return;}
  if(key.getExpiresAt()!=null&&!key.getExpiresAt().isAfter(LocalDateTime.now())){audit(key.getId(),key.getUser().getId(),"EXPIRED_API_KEY",request,"REJECTED");reject(response,HttpStatus.UNAUTHORIZED,"API_KEY_EXPIRED","The API key has expired.");return;}
  try{limiter.check(key.getId(),clientIp(request));}catch(com.eesara.drive.common.ApiException ex){reject(response,ex.getStatus(),ex.getCode(),ex.getMessage());return;}
  if(!allowed(request)){audit(key.getId(),key.getUser().getId(),"SCOPE_VIOLATION",request,"REJECTED");reject(response,HttpStatus.FORBIDDEN,"API_KEY_ENDPOINT_FORBIDDEN","This endpoint is not available to service API keys.");return;}
  key.setLastUsedAt(LocalDateTime.now()); repository.save(key);
  var authentication=new ApiKeyAuthenticationToken(new ApiKeyPrincipal(key)); authentication.setDetails(request.getRemoteAddr());
  SecurityContextHolder.getContext().setAuthentication(authentication); chain.doFilter(request,response);
  if(response.getStatus()>=400){String action="POST".equals(request.getMethod())&&"/api/files/upload".equals(request.getRequestURI())?"UPLOAD_REJECTED":"API_KEY_REQUEST_REJECTED";audit(key.getId(),key.getUser().getId(),action,request,"REJECTED");}
 }
 private boolean allowed(HttpServletRequest r){String p=r.getRequestURI(),m=r.getMethod();
  if("POST".equals(m)&&"/api/files/upload".equals(p))return true;
  if(p.matches("/api/files/[0-9a-fA-F-]{36}(/visibility|/move)?")&&(m.equals("PUT")||m.equals("DELETE")))return true;
  if(p.matches("/api/files/download/[0-9a-fA-F-]{36}")&&m.equals("GET"))return true;
  return p.equals("/api/files")&&m.equals("GET")||p.equals("/api/folders")&&m.equals("GET")||p.matches("/api/folders/[0-9a-fA-F-]{36}")&&m.equals("GET");
 }
 private void reject(HttpServletResponse r,HttpStatus status,String code,String message)throws IOException{r.setStatus(status.value());r.setContentType("application/json");r.getWriter().write("{\"success\":false,\"timestamp\":\""+Instant.now()+"\",\"status\":"+status.value()+",\"code\":\""+escape(code)+"\",\"message\":\""+escape(message)+"\",\"fieldErrors\":{}}");}
 private String escape(String value){return value.replace("\\","\\\\").replace("\"","\\\"");}
 private void audit(Long key,Long user,String action,HttpServletRequest r,String outcome){audits.save(AuditLog.builder().apiKeyId(key).userId(user).action(action).outcome(outcome).requestIp(clientIp(r)).build());}
 private String clientIp(HttpServletRequest r){String f=r.getHeader("X-Forwarded-For");return f==null?r.getRemoteAddr():f.split(",")[0].trim();}
}
