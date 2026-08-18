package com.eesara.drive.file.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileResponse {

    private String uuid;

    private String originalName;

    private String mimeType;

    private String extension;

    private Long fileSize;

    private String folderUuid;

    private LocalDateTime createdAt;

    private Boolean isPublic;

    private String url;

}
