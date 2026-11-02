package com.example.lms.controller;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.ProfileDto;
import com.example.lms.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Own-profile endpoints. The user id ALWAYS comes from the authenticated principal
 * (set by JwtAuthenticationFilter) - never from the URL or body - so one user can
 * never read or edit another user's profile through these routes.
 *
 * Mounted at /api/profile (NOT /api/auth/**, which is permitAll in SecurityConfig).
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ProfileDto.Response>> getMyProfile(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile fetched successfully.", profileService.getMyProfile(principal.getId())));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<ProfileDto.Response>> updateMyProfile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ProfileDto.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile updated successfully.", profileService.updateMyProfile(principal.getId(), request)));
    }

    @PostMapping(value = "/me/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileDto.Response>> uploadProfilePicture(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile picture updated successfully.",
                profileService.updateProfilePicture(principal.getId(), file)));
    }

    @PostMapping(value = "/me/background-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileDto.Response>> uploadBackgroundPicture(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                "Background picture updated successfully.",
                profileService.updateBackgroundPicture(principal.getId(), file)));
    }

    @DeleteMapping("/me/picture")
    public ResponseEntity<ApiResponse<ProfileDto.Response>> removeProfilePicture(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile picture removed.", profileService.removeProfilePicture(principal.getId())));
    }

    @DeleteMapping("/me/background-picture")
    public ResponseEntity<ApiResponse<ProfileDto.Response>> removeBackgroundPicture(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                "Background picture removed.", profileService.removeBackgroundPicture(principal.getId())));
    }
}