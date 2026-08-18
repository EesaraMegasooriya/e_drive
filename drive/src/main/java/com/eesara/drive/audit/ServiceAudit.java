package com.eesara.drive.audit;
import com.eesara.drive.apikey.ApiKeyPrincipal;
import com.eesara.drive.apikey.ApiKey;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class ServiceAudit {
 private final AuditLogRepository repository; private final HttpServletRequest request;
 public void record(String action,String target,String outcome,String detail){
  Object principal=SecurityContextHolder.getContext().getAuthentication()==null?null:SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  ApiKeyPrincipal key=principal instanceof ApiKeyPrincipal p?p:null;
  repository.save(AuditLog.builder().apiKeyId(key==null?null:key.getApiKeyId()).userId(key==null?null:key.getUser().getId())
   .action(action).targetUuid(target).outcome(outcome).detail(detail).requestIp(clientIp()).build());
 }
 public void recordForKey(ApiKey key,String action,String target,String outcome,String detail){repository.save(AuditLog.builder()
  .apiKeyId(key.getId()).userId(key.getUser().getId()).action(action).targetUuid(target).outcome(outcome).detail(detail).requestIp(clientIp()).build());}
 private String clientIp(){String forwarded=request.getHeader("X-Forwarded-For"); return forwarded==null?request.getRemoteAddr():forwarded.split(",")[0].trim();}
}
