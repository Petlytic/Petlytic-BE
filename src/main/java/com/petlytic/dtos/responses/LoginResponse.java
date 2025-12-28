package com.petlytic.dtos.responses;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class LoginResponse {
    private String accessToken;

    @JsonIgnore
    private String refreshToken;

    private long expiresIn;
}
