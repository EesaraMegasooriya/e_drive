package com.eesara.drive.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageServiceTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesContentAndProducesStableSha256Checksum() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.setLocation(temporaryDirectory.toString());
        LocalStorageService storage = new LocalStorageService(properties);

        String path = storage.save(new MockMultipartFile(
                "file", "hello.txt", "text/plain", "hello".getBytes()));

        assertThat(Files.readString(storage.load(path))).isEqualTo("hello");
        assertThat(storage.checksum(path))
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void cleanupLeavesRecentTemporaryFilesAlone() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.setLocation(temporaryDirectory.toString());
        LocalStorageService storage = new LocalStorageService(properties);
        Path recent = Files.createDirectories(temporaryDirectory.resolve(".tmp")).resolve("active.part");
        Files.writeString(recent, "partial");

        storage.cleanupTemporaryFiles();

        assertThat(recent).exists();
    }
}
