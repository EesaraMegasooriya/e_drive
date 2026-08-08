package com.eesara.drive.share.service;

import com.eesara.drive.file.entity.DriveFile;
import com.eesara.drive.file.repository.DriveFileRepository;
import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.folder.repository.FolderRepository;
import com.eesara.drive.security.service.CurrentUserService;
import com.eesara.drive.share.dto.AssetUrlResponse;
import com.eesara.drive.share.dto.CreateShareRequest;
import com.eesara.drive.share.dto.ShareResponse;
import com.eesara.drive.share.entity.ShareLink;
import com.eesara.drive.share.entity.ShareType;
import com.eesara.drive.share.repository.ShareLinkRepository;
import com.eesara.drive.storage.StorageService;
import com.eesara.drive.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final ShareLinkRepository shareLinkRepository;
    private final DriveFileRepository driveFileRepository;
    private final FolderRepository folderRepository;
    private final CurrentUserService currentUserService;
    private final StorageService storageService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    public ShareResponse createShare(CreateShareRequest request) {

        User owner = currentUserService.getCurrentUser();

        String token = generateToken();

        ShareLink.ShareLinkBuilder builder = ShareLink.builder()
                .owner(owner)
                .token(token)
                .type(request.getType());

        if (request.getType() == ShareType.FILE) {

            DriveFile file = driveFileRepository.findByUuid(request.getUuid())
                    .orElseThrow(() -> new RuntimeException("File not found"));

            if (!file.getOwner().getId().equals(owner.getId())) {
                throw new RuntimeException("Access denied");
            }
            if (!isPublicFile(file)) {
                throw new RuntimeException("Make the file or its folder public before creating a share link");
            }

            Optional<ShareLink> existing =
                    shareLinkRepository.findByFileAndActiveTrue(file);

            if (existing.isPresent()) {

                ShareLink share = existing.get();

                return ShareResponse.builder()
                        .uuid(share.getUuid())
                        .token(share.getToken())
                        .shareUrl(baseUrl + "/share/" + share.getToken())
                        .assetUrl(
                                baseUrl +
                                        "/api/public/assets/" +
                                        share.getToken() +
                                        "." +
                                        file.getExtension()
                        )
                        .build();
            }

            builder.file(file);

        } else {

            Folder folder = folderRepository.findByUuid(request.getUuid())
                    .orElseThrow(() -> new RuntimeException("Folder not found"));

            if (!folder.getOwner().getId().equals(owner.getId())) {
                throw new RuntimeException("Access denied");
            }

            Optional<ShareLink> existing =
                    shareLinkRepository.findByFolderAndActiveTrue(folder);

            if (existing.isPresent()) {

                ShareLink share = existing.get();

                return ShareResponse.builder()
                        .uuid(share.getUuid())
                        .token(share.getToken())
                        .shareUrl(baseUrl + "/share/" + share.getToken())
                        .assetUrl(null)
                        .build();
            }

            builder.folder(folder);
        }

        ShareLink share = shareLinkRepository.save(builder.build());

        ShareResponse.ShareResponseBuilder response =
                ShareResponse.builder()
                        .uuid(share.getUuid())
                        .token(share.getToken())
                        .shareUrl(baseUrl + "/share/" + share.getToken());

        if (share.getType() == ShareType.FILE) {

            response.assetUrl(
                    baseUrl +
                            "/api/public/assets/" +
                            share.getToken() +
                            "." +
                            share.getFile().getExtension()
            );

        } else {

            response.assetUrl(null);

        }

        return response.build();
    }

    @Override
    public AssetUrlResponse getFileAssetUrl(String fileUuid) {

        User owner = currentUserService.getCurrentUser();

        DriveFile file = driveFileRepository.findByUuid(fileUuid)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!file.getOwner().getId().equals(owner.getId())) {
            throw new RuntimeException("Access denied");
        }
        if (!isPublicFile(file)) {
            throw new RuntimeException("This file is private");
        }

        ShareLink share = shareLinkRepository
        .findByFileAndActiveTrue(file)
        .orElseGet(() -> {

            ShareLink newShare = ShareLink.builder()
                    .owner(owner)
                    .file(file)
                    .type(ShareType.FILE)
                    .token(generateToken())
                    .build();

            return shareLinkRepository.save(newShare);
        });

        return AssetUrlResponse.builder()
                .name(file.getOriginalName())
                .url(
                        baseUrl +
                                "/api/public/assets/" +
                                share.getToken() +
                                "." +
                                file.getExtension()
                )
                .build();
    }

    @Override
public List<AssetUrlResponse> getFolderAssetUrls(String folderUuid) {

    User owner = currentUserService.getCurrentUser();

    Folder folder = folderRepository.findByUuid(folderUuid)
            .orElseThrow(() -> new RuntimeException("Folder not found"));

    if (!folder.getOwner().getId().equals(owner.getId())) {
        throw new RuntimeException("Access denied");
    }
    if (!Boolean.TRUE.equals(folder.getIsPublic())) {
        throw new RuntimeException("This folder is private");
    }

    return driveFileRepository.findByFolderAndOwner(folder, owner)
            .stream()
            .map(file -> {

                ShareLink share = shareLinkRepository
                        .findByFileAndActiveTrue(file)
                        .orElseGet(() -> {

                            ShareLink newShare = ShareLink.builder()
                                    .owner(owner)
                                    .file(file)
                                    .type(ShareType.FILE)
                                    .token(generateToken())
                                    .build();

                            return shareLinkRepository.save(newShare);
                        });

                return AssetUrlResponse.builder()
                        .name(file.getOriginalName())
                        .url(
                                baseUrl +
                                "/api/public/assets/" +
                                share.getToken() +
                                "." +
                                file.getExtension()
                        )
                        .build();

            })
            .toList();
}
   

    @Override
    public ShareLink getByToken(String token) {

        ShareLink share = shareLinkRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Share not found"));

        if (!Boolean.TRUE.equals(share.getActive())) {
            throw new RuntimeException("Share disabled");
        }

        if (share.getExpiresAt() != null &&
                share.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Share expired");
        }

        return share;
    }

    @Override
    public Resource getSharedResource(String token) {

        ShareLink share = getByToken(token);

        if (share.getType() != ShareType.FILE) {
            throw new RuntimeException("Not a file share");
        }
        if (!isPublicFile(share.getFile())) {
            throw new RuntimeException("This file is private");
        }

        return storageService.loadAsResource(
                share.getFile().getStoragePath()
        );
    }

    private String generateToken() {

        byte[] bytes = new byte[16];

        new SecureRandom().nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private boolean isPublicFile(DriveFile file) {
        return Boolean.TRUE.equals(file.getIsPublic())
                || (file.getFolder() != null && Boolean.TRUE.equals(file.getFolder().getIsPublic()));
    }
}
