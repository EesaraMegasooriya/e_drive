package com.eesara.drive.share.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssetUrlResponse {

    private String name;

    private String url;

}