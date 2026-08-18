package com.eesara.drive.file.controller;
import com.eesara.drive.common.ApiException;
import com.eesara.drive.file.repository.DriveFileRepository;
import com.eesara.drive.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
@RestController @RequiredArgsConstructor
public class PublicFileController {
 private final DriveFileRepository files; private final StorageService storage;
 @GetMapping("/files/{uuid}/{storedName}")
 public ResponseEntity<Resource> get(@PathVariable String uuid,@PathVariable String storedName){
  var file=files.findByUuid(uuid).filter(f->!Boolean.TRUE.equals(f.getDeleted())&&Boolean.TRUE.equals(f.getIsPublic())&&f.getStoredName().equals(storedName))
   .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"FILE_NOT_FOUND","Public file not found."));
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.getMimeType())).contentLength(file.getFileSize())
   .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
   .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,"*").header("X-Content-Type-Options","nosniff")
   .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(file.getOriginalName()).build().toString())
   .body(storage.loadAsResource(file.getStoragePath()));
 }
}
