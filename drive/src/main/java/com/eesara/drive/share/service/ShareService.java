package com.eesara.drive.share.service;

import com.eesara.drive.share.dto.AssetUrlResponse;
import com.eesara.drive.share.dto.CreateShareRequest;
import com.eesara.drive.share.dto.ShareResponse;
import com.eesara.drive.share.dto.PublicFolderFileResponse;
import com.eesara.drive.share.dto.PublicFolderMetadataResponse;
import com.eesara.drive.share.entity.ShareLink;
import com.eesara.drive.file.dto.FileDownloadResponse;
import org.springframework.core.io.Resource;

import java.util.List;

public interface ShareService {

    ShareResponse createShare(CreateShareRequest request);

    AssetUrlResponse getFileAssetUrl(String fileUuid);

    List<AssetUrlResponse> getFolderAssetUrls(String folderUuid);

    List<PublicFolderFileResponse> getPublicFolderFiles(String token, int offset, int limit);

    List<PublicFolderFileResponse> getAllPublicFolderFiles(String token);

    PublicFolderMetadataResponse getPublicFolderMetadata(String token);

    ShareLink getByToken(String token);

    Resource getSharedResource(String token);

    Resource getSharedFolderResource(String token, String fileUuid);

    FileDownloadResponse getSharedFolderDownload(String token, String fileUuid);
}
