package com.eesara.drive.share.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ShareResponse {

    private String uuid;
private String token;

private String shareUrl;

private String assetUrl;

private String contentsUrl;

}
