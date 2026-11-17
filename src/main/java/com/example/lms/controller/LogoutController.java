package com.example.lms.controller;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.ApiResponse;
import com.example.lms.service.LoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LogoutController {

    private final LoginService loginService;

    // Any logged-in user. Tokens are stateless, so this only marks the user offline for the dashboard.
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal AuthPrincipal principal) {
        loginService.logout(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully."));
    }

    // Sent by the open browser every ~45 s. No heartbeat for 2 minutes = offline on the dashboard.
    @PostMapping("/heartbeat")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> heartbeat(@AuthenticationPrincipal AuthPrincipal principal) {
        loginService.heartbeat(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("OK."));
    }
}