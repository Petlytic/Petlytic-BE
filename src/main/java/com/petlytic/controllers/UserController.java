package com.petlytic.controllers;

import com.petlytic.dtos.responses.ApiResponse;
import com.petlytic.models.User;
import com.petlytic.services.UserService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RequestMapping("/users")
@RestController
@RequiredArgsConstructor
@Tag(name = "User", description = "User management endpoints")
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();

        return ResponseEntity.ok(ApiResponse.<User>builder()
                .result(currentUser)
                .message("User profile retrieved successfully")
                .build());
    }

    @GetMapping("/")
    public ResponseEntity<ApiResponse<List<User>>> allUsers() {
        List<User> users = userService.allUsers();

        return ResponseEntity.ok(ApiResponse.<List<User>>builder()
                .result(users)
                .message("Users list retrieved successfully")
                .build());
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();

        String newAvatarUrl = userService.updateUserAvatar(currentUser.getId(), file);

        return ResponseEntity.ok(ApiResponse.<String>builder()
                .result(newAvatarUrl)
                .message("Avatar updated successfully")
                .build());
    }
}