package com.eesara.drive.folder.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFolderRequest {

    @NotBlank(message = "Folder name is required")
    private String name;

    /**
     * null = Root Folder
     */
    private String parentUuid;

}