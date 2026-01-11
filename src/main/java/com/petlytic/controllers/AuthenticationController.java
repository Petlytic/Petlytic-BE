package com.petlytic.controllers;

import com.petlytic.dtos.requests.*;
import com.petlytic.dtos.responses.ApiResponse;
import com.petlytic.dtos.responses.LoginResponse;
import com.petlytic.dtos.responses.UserResponseDTO;
import com.petlytic.services.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/auth")
@RestController
@RequiredArgsConstructor
public class AuthenticationController {
    private final AuthenticationService authenticationService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponseDTO>> register(@RequestBody @Valid RegisterUserDTO registerUserDto) {
        UserResponseDTO registeredUser = authenticationService.signup(registerUserDto);

        return ResponseEntity.ok(ApiResponse.<UserResponseDTO>builder()
                .result(registeredUser)
                .message("User registered successfully")
                .build());
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> authenticate(@RequestBody LoginUserDTO loginUserDto) {
        LoginResponse loginResponse = authenticationService.authenticate(loginUserDto);

        ResponseCookie cookie = createRefreshTokenCookie(loginResponse.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.<LoginResponse>builder()
                        .result(loginResponse)
                        .message("Login successfully")
                        .build());
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verifyUser(@RequestBody @Valid VerifyUserDTO verifyUserDto) {
        authenticationService.verifyUser(verifyUserDto);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Account verified successfully")
                .build());
    }

    // @RateLimited(maxAttempts = 3, duration = 15, unit = TimeUnit.MINUTES)
    @PostMapping("/resend")
    public ResponseEntity<ApiResponse<Void>> resendVerificationCode(@RequestBody @Valid ResendVerificationDTO resendVerificationDTO) {
        authenticationService.resendVerificationCode(resendVerificationDTO.getEmail());

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Verification code sent to " + resendVerificationDTO.getEmail())
                .build());
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @CookieValue(name = "refresh_token") String refreshToken
    ) {
        RefreshTokenDTO refreshTokenDTO = new RefreshTokenDTO();
        refreshTokenDTO.setRefreshToken(refreshToken);

        LoginResponse loginResponse = authenticationService.refreshToken(refreshTokenDTO);

        ResponseCookie newCookie = createRefreshTokenCookie(loginResponse.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newCookie.toString())
                .body(ApiResponse.<LoginResponse>builder()
                        .result(loginResponse)
                        .message("Token refreshed successfully")
                        .build());
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithGoogle(@RequestBody GoogleLoginDTO googleLoginDTO) {
        LoginResponse response = authenticationService.loginWithGoogle(googleLoginDTO);

        ResponseCookie newCookie = createRefreshTokenCookie(response.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newCookie.toString())
                .body(ApiResponse.<LoginResponse>builder()
                        .result(response)
                        .message("Google login successfully")
                        .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken
    ) {
        if (refreshToken != null) {
            authenticationService.logout(refreshToken);
        }

        ResponseCookie cleanCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanCookie.toString())
                .body(ApiResponse.<Void>builder()
                        .message("Logged out successfully")
                        .build());
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordDTO dto) {
        authenticationService.forgotPassword(dto);
        return ResponseEntity.ok("Reset code sent to email");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordDTO dto) {
        authenticationService.resetPassword(dto);
        return ResponseEntity.ok("Password reset successfully");
    }


    // Private Helpers

    private ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(false) //false when localhost, true if production
                .path("/")
                .maxAge(604800) // 7 days
                .sameSite("Strict")
                .build();
    }
}