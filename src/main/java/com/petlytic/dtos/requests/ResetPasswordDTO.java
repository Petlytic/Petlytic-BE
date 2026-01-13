package com.petlytic.dtos.requests;

import lombok.Getter;

@Getter
public class ResetPasswordDTO {
    private String email;
    private String resetCode;
    private String newPassword;
}
