package com.eesara.drive.drive.controller;

import com.eesara.drive.drive.dto.DriveResponse;
import com.eesara.drive.drive.service.DriveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/drive")
@RequiredArgsConstructor
public class DriveController {

    private final DriveService driveService;

    @GetMapping
    public ResponseEntity<DriveResponse> getDrive(

            @RequestParam(required = false)
            String folderUuid

    ) {

        return ResponseEntity.ok(
                driveService.getDrive(folderUuid)
        );
    }
}