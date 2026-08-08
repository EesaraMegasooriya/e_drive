package com.eesara.drive.auth.dto;

import com.eesara.drive.user.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String uuid;

    private String name;

    private String email;

    private Role role;

    private Long storageLimit;

    private Long usedStorage;
}