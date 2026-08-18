package com.eesara.drive.file.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadFileResponse {

    private String uuid;

    private String originalName;

    private String name;

    private String mimeType;

    private Long size;

    private String url;

    private Boolean isPublic;

    private String folderUuid;

    private LocalDateTime createdAt;

    private Long fileSize;

    private String message;

}
