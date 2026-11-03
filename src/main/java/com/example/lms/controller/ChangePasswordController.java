package com.example.lms.controller;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.ChangePasswordDto;
import com.example.lms.service.ChangePasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/change-password")
@RequiredArgsConstructor
public class ChangePasswordController {

    private final ChangePasswordService changePasswordService;

    // Any logged-in user: SUPER_ADMIN, ADMIN, Instructor, Learner
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ChangePasswordDto.ChangePasswordRequest request) {
        changePasswordService.changePassword(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully."));
    }
}