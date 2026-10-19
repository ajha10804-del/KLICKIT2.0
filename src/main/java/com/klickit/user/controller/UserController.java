package com.klickit.user.controller;

import com.klickit.common.dto.ApiResponse;
import com.klickit.user.dto.UpdateProfileRequest;
import com.klickit.user.dto.UserProfileResponse;
import com.klickit.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "Authenticated customer profile management APIs")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get current user profile", description = "Retrieve profile details for authenticated user derived securely from session")
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException("User is not authenticated");
        }
        UserProfileResponse profile = userService.getProfile(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @Operation(summary = "Update current user profile", description = "Update customer name or phone number for authenticated user derived securely from session")
    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            Principal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException("User is not authenticated");
        }
        UserProfileResponse profile = userService.updateProfile(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", profile));
    }
}
