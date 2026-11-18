package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.LoginDto;
import com.example.lms.dto.TwoFactorDto;
import com.example.lms.service.LoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public (covered by the existing "/api/auth/**" permitAll rule): the caller has proven the password
 * but has no JWT yet. Rate-limited by RateLimitingFilter ("/api/auth/2fa").
 */
@RestController
@RequestMapping("/api/auth/2fa/login")
@RequiredArgsConstructor
public class TwoFactorLoginController {

    private final LoginService loginService;

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<LoginDto.LoginResponse>> verify(
            @Valid @RequestBody TwoFactorDto.LoginVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login successful.", loginService.verifyTwoFactorLogin(request)));
    }

    @PostMapping("/resend")
    public ResponseEntity<ApiResponse<Void>> resend(
            @Valid @RequestBody TwoFactorDto.LoginResendRequest request) {
        loginService.resendTwoFactorLoginOtp(request);
        return ResponseEntity.ok(ApiResponse.success("A new verification code has been sent."));
    }
}
