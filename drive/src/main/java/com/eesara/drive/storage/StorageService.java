package com.eesara.drive.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

public interface StorageService {

    String save(MultipartFile file) throws IOException;

    Path load(String storagePath);

    Resource loadAsResource(String storagePath);

    void delete(String storagePath) throws IOException;

}