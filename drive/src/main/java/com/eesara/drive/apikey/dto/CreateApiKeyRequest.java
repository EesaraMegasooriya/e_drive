package com.eesara.drive.apikey.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.Set;
public record CreateApiKeyRequest(@NotBlank @Size(max=150) String name,@NotBlank String userUuid,
 @NotEmpty Set<String> scopes,String folderUuid,@Future LocalDateTime expiresAt){}
