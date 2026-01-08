package com.petlytic.models.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {
    // System Errors
    UNCATEGORIZED_EXCEPTION(9999, "An undefined system error occurred", HttpStatus.INTERNAL_SERVER_ERROR),

    // Input / Validation Errors
    INVALID_KEY(1001, "Invalid value for %s", HttpStatus.BAD_REQUEST),

    // Resource Errors
    RESOURCE_NOT_FOUND(1002, "%s not found", HttpStatus.NOT_FOUND),
    USER_EXISTED(1003, "User already exists", HttpStatus.CONFLICT),

    // Account Status Errors
    ACCOUNT_NOT_VERIFIED(1004, "Account email has not been verified", HttpStatus.FORBIDDEN),
    ACCOUNT_ALREADY_VERIFIED(1005, "Account has already been verified", HttpStatus.CONFLICT),
    CODE_EXPIRED(1008, "Verification code has expired", HttpStatus.BAD_REQUEST),

    // Authentication Errors
    INVALID_CREDENTIALS(1006, "Invalid email or password", HttpStatus.UNAUTHORIZED),
    GOOGLE_LOGIN_FAILED(1009, "Google login failed", HttpStatus.UNAUTHORIZED),
    MAIL_VERIFICATION_FAILED(1012, "Email verification failed", HttpStatus.BAD_REQUEST),

    // Session / Token Errors
    TOKEN_EXPIRED(1007, "Session has expired", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(1010, "Invalid token", HttpStatus.UNAUTHORIZED),
    TOKEN_REVOKED(1011, "Token has been revoked or logged in from another device", HttpStatus.UNAUTHORIZED),
    ;

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}