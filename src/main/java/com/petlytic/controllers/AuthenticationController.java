package com.petlytic.controllers;

import com.petlytic.dtos.requests.*;
import com.petlytic.dtos.responses.LoginResponse;
import com.petlytic.models.User;
import com.petlytic.services.AuthenticationService;
import com.petlytic.services.JwtService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/auth")
@RestController
@RequiredArgsConstructor
public class AuthenticationController {
    private final AuthenticationService authenticationService;

    @PostMapping("/signup")
    public ResponseEntity<User> register(@RequestBody @Valid RegisterUserDTO registerUserDto) {
        User registeredUser = authenticationService.signup(registerUserDto);
        return ResponseEntity.ok(registeredUser);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> authenticate(@RequestBody LoginUserDTO loginUserDto) {
        LoginResponse loginResponse = authenticationService.authenticate(loginUserDto);

        // Tạo HttpOnly Cookie
        ResponseCookie cookie = ResponseCookie.from("refresh_token", loginResponse.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(604800)
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(loginResponse);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyUser(@RequestBody VerifyUserDTO verifyUserDto) {
        try {
            authenticationService.verifyUser(verifyUserDto);
            return ResponseEntity.ok("Account verified successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/resend")
    public ResponseEntity<?> resendVerificationCode(@RequestParam String email) {
        try {
            authenticationService.resendVerificationCode(email);
            return ResponseEntity.ok("Verification code sent");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<LoginResponse> refreshToken(
            @CookieValue(name = "refresh_token") String refreshToken
    ) {
        RefreshTokenDTO refreshTokenDTO = new RefreshTokenDTO();
        refreshTokenDTO.setRefreshToken(refreshToken);

        LoginResponse loginResponse = authenticationService.refreshToken(refreshTokenDTO);

        ResponseCookie newCookie = ResponseCookie.from("refresh_token", loginResponse.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(604800)
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newCookie.toString())
                .body(loginResponse);
    }

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> loginWithGoogle(@RequestBody GoogleLoginDTO googleLoginDTO) {
        return ResponseEntity.ok(authenticationService.loginWithGoogle(googleLoginDTO));
    }
}
