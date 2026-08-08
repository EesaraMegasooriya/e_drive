package com.eesara.drive.folder.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenameFolderRequest {

    @NotBlank(message = "Folder name is required")
    private String name;

}