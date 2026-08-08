package com.eesara.drive.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.core.io.Resource;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDownloadResponse {

    private Resource resource;

    private String fileName;

    private String mimeType;

    private Long fileSize;

}