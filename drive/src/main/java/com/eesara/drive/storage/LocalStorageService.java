package com.eesara.drive.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private final StorageProperties properties;

    @Override
    public String save(MultipartFile file) throws IOException {

        String extension = "";

        String original = file.getOriginalFilename();

        if (original != null && original.contains(".")) {
            extension = original.substring(original.lastIndexOf("."));
        }

        String filename = UUID.randomUUID() + extension;

        String folder = filename.substring(0, 2);

        Path directory = Paths.get(properties.getLocation(), folder);

        Files.createDirectories(directory);

        Path destination = directory.resolve(filename);

        Files.copy(
                file.getInputStream(),
                destination,
                StandardCopyOption.REPLACE_EXISTING
        );

        return folder + "/" + filename;
    }

    @Override
    public Path load(String storagePath) {

        return Paths.get(
                properties.getLocation(),
                storagePath
        );
    }

    @Override
    public Resource loadAsResource(String storagePath) {

        try {

            Path file = load(storagePath);

            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            }

            throw new RuntimeException("File not found");

        } catch (MalformedURLException e) {

            throw new RuntimeException("Unable to load file", e);

        }

    }

    @Override
    public void delete(String storagePath) throws IOException {

        Files.deleteIfExists(
                load(storagePath)
        );

    }

}