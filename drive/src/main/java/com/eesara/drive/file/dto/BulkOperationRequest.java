package com.eesara.drive.file.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class BulkOperationRequest {
    private List<String> fileUuids = new ArrayList<>();
    private List<String> folderUuids = new ArrayList<>();
    private String destinationFolderUuid;
}
