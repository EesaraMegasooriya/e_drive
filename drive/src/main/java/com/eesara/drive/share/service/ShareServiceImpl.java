package com.eesara.drive.share.service;

import com.eesara.drive.file.entity.DriveFile;
import com.eesara.drive.file.dto.FileDownloadResponse;
import com.eesara.drive.file.repository.DriveFileRepository;
import com.eesara.drive.folder.entity.Folder;
import com.eesara.drive.folder.repository.FolderRepository;
import com.eesara.drive.security.service.CurrentUserService;
import com.eesara.drive.share.dto.AssetUrlResponse;
import com.eesara.drive.share.dto.CreateShareRequest;
import com.eesara.drive.share.dto.ShareResponse;
import com.eesara.drive.share.dto.PublicFolderFileResponse;
import com.eesara.drive.share.dto.PublicFolderMetadataResponse;
import com.eesara.drive.share.entity.ShareLink;
import com.eesara.drive.share.entity.ShareType;
import com.eesara.drive.share.repository.ShareLinkRepository;
import com.eesara.drive.storage.StorageService;
import com.eesara.drive.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

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

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

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
                        .shareUrl(fileShareUrl(share.getToken()))
                        .assetUrl(
                                baseUrl +
                                        "/api/public/assets/" +
                                        share.getToken() +
                                        "." +
                                        file.getExtension()
                        )
                        .contentsUrl(null)
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
                        .shareUrl(folderShareUrl(share.getToken()))
                        .assetUrl(null)
                        .contentsUrl(folderContentsUrl(share.getToken()))
                        .linksUrl(folderLinksUrl(share.getToken()))
                        .build();
            }

            builder.folder(folder);
        }

        ShareLink share = shareLinkRepository.save(builder.build());

        ShareResponse.ShareResponseBuilder response =
                ShareResponse.builder()
                        .uuid(share.getUuid())
                        .token(share.getToken())
                        .shareUrl(share.getType() == ShareType.FOLDER
                                ? folderShareUrl(share.getToken())
                                : fileShareUrl(share.getToken()));

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
            response.contentsUrl(folderContentsUrl(share.getToken()));
            response.linksUrl(folderLinksUrl(share.getToken()));

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

    ShareLink folderShare = shareLinkRepository.findByFolderAndActiveTrue(folder)
            .orElseGet(() -> shareLinkRepository.save(ShareLink.builder()
                    .owner(owner)
                    .folder(folder)
                    .type(ShareType.FOLDER)
                    .token(generateToken())
                    .build()));

    List<AssetUrlResponse> links = new ArrayList<>();
    collectFolderAssetUrls(folder, "", folderShare.getToken(), links);
    links.sort(java.util.Comparator.comparing(AssetUrlResponse::getName,
            String.CASE_INSENSITIVE_ORDER));
    return links;
}

    @Override
    @Transactional(readOnly = true)
    public List<PublicFolderFileResponse> getPublicFolderFiles(String token, int offset, int limit) {

        List<PublicFolderFileResponse> files = getAllPublicFolderFiles(token);
        int from = Math.min(Math.max(0, offset), files.size());
        int to = Math.min(from + Math.min(Math.max(1, limit), 250), files.size());
        return files.subList(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicFolderFileResponse> getAllPublicFolderFiles(String token) {

        ShareLink share = getByToken(token);

        if (share.getType() != ShareType.FOLDER || share.getFolder() == null) {
            throw new RuntimeException("Not a folder share");
        }

        List<PublicFolderFileResponse> files = new ArrayList<>();
        collectPublicFolderFiles(share.getFolder(), "", token, files);
        files.sort(java.util.Comparator.comparing(PublicFolderFileResponse::getPath,
                String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    @Override
    @Transactional(readOnly = true)
    public PublicFolderMetadataResponse getPublicFolderMetadata(String token) {
        ShareLink share = getByToken(token);
        if (share.getType() != ShareType.FOLDER || share.getFolder() == null) {
            throw new RuntimeException("Not a folder share");
        }
        Folder folder = share.getFolder();
        return PublicFolderMetadataResponse.builder()
                .name(folder.getName())
                .coverImageUrl(folder.getCoverImageUrl())
                .coverIcon(folder.getCoverIcon())
                .build();
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

    @Override
    @Transactional(readOnly = true)
    public Resource getSharedFolderResource(String token, String fileUuid) {

        return getSharedFolderDownload(token, fileUuid).getResource();
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResponse getSharedFolderDownload(String token, String fileUuid) {

        ShareLink share = getByToken(token);

        if (share.getType() != ShareType.FOLDER || share.getFolder() == null) {
            throw new RuntimeException("Not a folder share");
        }

        DriveFile file = driveFileRepository.findByUuid(fileUuid)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!belongsToFolderTree(file.getFolder(), share.getFolder())) {
            throw new RuntimeException("File is not part of this shared folder");
        }

        return FileDownloadResponse.builder()
                .resource(storageService.loadAsResource(file.getStoragePath()))
                .fileName(file.getOriginalName())
                .mimeType(file.getMimeType())
                .fileSize(file.getFileSize())
                .build();
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

    private void collectPublicFolderFiles(
            Folder folder,
            String parentPath,
            String token,
            List<PublicFolderFileResponse> files
    ) {
        String folderPath = parentPath.isBlank()
                ? folder.getName()
                : parentPath + "/" + folder.getName();

        driveFileRepository.findByFolder(folder).stream()
                .filter(file -> !Boolean.TRUE.equals(file.getDeleted()))
                .forEach(file -> files.add(PublicFolderFileResponse.builder()
                        .uuid(file.getUuid())
                        .name(file.getOriginalName())
                        .extension(file.getExtension())
                        .mimeType(file.getMimeType())
                        .size(file.getFileSize())
                        .path(folderPath + "/" + file.getOriginalName())
                        .url(folderAssetUrl(token, file))
                        .build()));

        folder.getChildren().stream()
                .filter(child -> !Boolean.TRUE.equals(child.getIsDeleted()))
                .forEach(child -> collectPublicFolderFiles(child, folderPath, token, files));
    }

    private void collectFolderAssetUrls(
            Folder folder,
            String parentPath,
            String token,
            List<AssetUrlResponse> links
    ) {
        String folderPath = parentPath.isBlank()
                ? folder.getName()
                : parentPath + "/" + folder.getName();
        driveFileRepository.findByFolder(folder).stream()
                .filter(file -> !Boolean.TRUE.equals(file.getDeleted()))
                .forEach(file -> links.add(AssetUrlResponse.builder()
                        .name(folderPath + "/" + file.getOriginalName())
                        .url(folderAssetUrl(token, file))
                        .build()));

        folder.getChildren().stream()
                .filter(child -> !Boolean.TRUE.equals(child.getIsDeleted()))
                .forEach(child -> collectFolderAssetUrls(child, folderPath, token, links));
    }

    private boolean belongsToFolderTree(Folder fileFolder, Folder sharedFolder) {
        Folder current = fileFolder;

        while (current != null) {
            if (current.getId().equals(sharedFolder.getId())) {
                return true;
            }
            current = current.getParent();
        }

        return false;
    }

    private String folderContentsUrl(String token) {
        return baseUrl + "/api/public/folders/" + token;
    }

    private String folderLinksUrl(String token) {
        return folderContentsUrl(token) + "/links";
    }

    private String folderShareUrl(String token) {
        return frontendUrl.replaceAll("/$", "") + "/share/folder/" + token;
    }

    private String fileShareUrl(String token) {
        return frontendUrl.replaceAll("/$", "") + "/share/" + token;
    }

    private String folderAssetUrl(String token, DriveFile file) {
        String extension = file.getExtension();
        return folderContentsUrl(token) + "/assets/" + file.getUuid()
                + (extension == null || extension.isBlank() ? "/content" : "." + extension);
    }
}
