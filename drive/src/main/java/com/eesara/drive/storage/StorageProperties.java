package com.eesara.drive.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /**
     * Example:
     * storage.location=storage
     * storage.location=/data/storage
     */
    private String location = "storage";

}