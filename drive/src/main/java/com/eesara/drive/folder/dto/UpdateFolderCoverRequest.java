package com.eesara.drive.folder.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateFolderCoverRequest {
    private String coverImageUrl;
    private String coverIcon;
}
