package com.eesara.drive.folder.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderResponse {

    private String uuid;

    private String name;

    private String parentUuid;

    private String path;

    private Long totalFolders;

    private Long totalFiles;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Boolean isPublic;

}
