package com.eesara.drive.apikey.dto;
import java.time.LocalDateTime;
import java.util.Set;
public record ApiKeyResponse(Long id,String name,String keyPrefix,Set<String> scopes,String userUuid,String folderUuid,
 boolean isActive,LocalDateTime expiresAt,LocalDateTime lastUsedAt,LocalDateTime createdAt){}
