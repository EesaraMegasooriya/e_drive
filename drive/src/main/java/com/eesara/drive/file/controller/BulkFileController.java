package com.eesara.drive.file.controller;

import com.eesara.drive.file.dto.BulkOperationRequest;
import com.eesara.drive.file.service.BulkFileService;
import com.eesara.drive.security.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

@RestController
@RequestMapping("/api/bulk")
@RequiredArgsConstructor
public class BulkFileController {
    private final BulkFileService bulk;
    private final CurrentUserService currentUserService;

    @PostMapping("/move")
    public ResponseEntity<Void> move(@RequestBody BulkOperationRequest request) {
        bulk.move(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/copy")
    public ResponseEntity<Void> copy(@RequestBody BulkOperationRequest request) throws IOException {
        bulk.copy(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/delete")
    public ResponseEntity<Void> delete(@RequestBody BulkOperationRequest request) throws IOException {
        bulk.delete(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/download", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> download(@RequestBody BulkOperationRequest request) {
        var owner = currentUserService.getCurrentUser();
        StreamingResponseBody body = output -> bulk.writeZip(request, owner, output);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=drive-selection.zip")
                .body(body);
    }
}
