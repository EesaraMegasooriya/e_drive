package com.eesara.drive.share.service;

import com.eesara.drive.share.dto.AssetUrlResponse;
import com.eesara.drive.share.dto.CreateShareRequest;
import com.eesara.drive.share.dto.ShareResponse;
import com.eesara.drive.share.entity.ShareLink;
import org.springframework.core.io.Resource;

import java.util.List;

public interface ShareService {

    ShareResponse createShare(CreateShareRequest request);

    AssetUrlResponse getFileAssetUrl(String fileUuid);

    List<AssetUrlResponse> getFolderAssetUrls(String folderUuid);

    ShareLink getByToken(String token);

    Resource getSharedResource(String token);
}