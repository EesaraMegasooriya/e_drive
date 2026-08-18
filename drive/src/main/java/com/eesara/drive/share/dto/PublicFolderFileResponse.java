package com.eesara.drive.share.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PublicFolderFileResponse {

    private String uuid;
    private String name;
    private String extension;
    private String mimeType;
    private Long size;
    private String path;
    private String url;
    private String playbackUrl;
}
