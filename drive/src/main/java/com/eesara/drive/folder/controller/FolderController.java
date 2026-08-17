package com.eesara.drive.folder.controller;

import com.eesara.drive.folder.dto.CreateFolderRequest;
import com.eesara.drive.folder.dto.FolderResponse;
import com.eesara.drive.folder.dto.RenameFolderRequest;
import com.eesara.drive.folder.dto.UpdateFolderCoverRequest;
import com.eesara.drive.folder.service.FolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    

    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(
            @Valid @RequestBody CreateFolderRequest request
    ) {

        FolderResponse response = folderService.createFolder(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<FolderResponse>> listFolders(
            @RequestParam(required = false) String parentUuid
    ) {

        return ResponseEntity.ok(
                folderService.listFolders(parentUuid)
        );
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<FolderResponse> getFolder(
            @PathVariable String uuid
    ) {

        return ResponseEntity.ok(
                folderService.getFolder(uuid)
        );
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<FolderResponse> renameFolder(
            @PathVariable String uuid,
            @Valid @RequestBody RenameFolderRequest request
    ) {

        return ResponseEntity.ok(
                folderService.renameFolder(uuid, request)
        );
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable String uuid
    ) {

        folderService.deleteFolder(uuid);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{uuid}/visibility")
    public ResponseEntity<FolderResponse> setVisibility(@PathVariable String uuid, @RequestParam boolean isPublic) {
        return ResponseEntity.ok(folderService.setPublic(uuid, isPublic));
    }

    @PutMapping("/{uuid}/move")
    public ResponseEntity<FolderResponse> moveFolder(
            @PathVariable String uuid,
            @RequestParam(required = false) String parentUuid
    ) {
        return ResponseEntity.ok(folderService.moveFolder(uuid, parentUuid));
    }

    @PostMapping("/{uuid}/copy")
    public ResponseEntity<FolderResponse> copyFolder(
            @PathVariable String uuid,
            @RequestParam(required = false) String parentUuid
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(folderService.copyFolder(uuid, parentUuid));
    }

    @PutMapping("/{uuid}/cover")
    public ResponseEntity<FolderResponse> updateCover(
            @PathVariable String uuid,
            @RequestBody UpdateFolderCoverRequest request
    ) {
        return ResponseEntity.ok(folderService.updateCover(uuid, request));
    }
}
