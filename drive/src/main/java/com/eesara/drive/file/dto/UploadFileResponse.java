package com.eesara.drive.file.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadFileResponse {

    private String uuid;

    private String originalName;

    private Long fileSize;

    private String message;

}