package com.eesara.drive.drive.service;

import com.eesara.drive.drive.dto.DriveResponse;

public interface DriveService {

    DriveResponse getDrive(String folderUuid);

}