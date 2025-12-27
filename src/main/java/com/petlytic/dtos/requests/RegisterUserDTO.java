package com.petlytic.dtos.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterUserDTO {
    @NotBlank(message = "Email is required")
    @Email(message = "Email is not valid")
    private String email;

    @Size(min = 6, message = "Password must contain at least 6 characters")
    private String password;

    private String username;
}
