package com.eesara.drive.share.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PublicFolderMetadataResponse {
    private String name;
    private String coverImageUrl;
    private String coverIcon;
}
