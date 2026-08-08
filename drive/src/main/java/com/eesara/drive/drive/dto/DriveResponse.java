package com.eesara.drive.drive.dto;

import com.eesara.drive.file.dto.FileResponse;
import com.eesara.drive.folder.dto.FolderResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveResponse {

    private FolderResponse currentFolder;

    private List<FolderResponse> folders;

    private List<FileResponse> files;

}