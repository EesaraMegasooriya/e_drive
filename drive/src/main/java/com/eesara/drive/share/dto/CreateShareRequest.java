package com.eesara.drive.share.dto;

import com.eesara.drive.share.entity.ShareType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateShareRequest {

    /**
     * UUID of file OR folder
     */
    private String uuid;

    /**
     * FILE / FOLDER
     */
    private ShareType type;

}