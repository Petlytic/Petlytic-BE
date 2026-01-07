package com.petlytic.dtos.responses;

import com.petlytic.models.enums.Role;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
public class UserResponseDTO {
    private UUID id;
    private String username;
    private String email;
    private Role role;

    private String avatarUrl;
    private String phoneNumber;
    private boolean active;
}
