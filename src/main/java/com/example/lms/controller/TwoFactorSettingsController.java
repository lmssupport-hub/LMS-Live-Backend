package com.example.lms.controller;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.TwoFactorDto;
import com.example.lms.service.TwoFactorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Profile > Settings > "2FA Verification Method". Any logged-in user (anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/profile/2fa")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TwoFactorSettingsController {

    private final TwoFactorService twoFactorService;

    @GetMapping
    public ResponseEntity<ApiResponse<TwoFactorDto.Status>> status(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("2FA status.", twoFactorService.status(principal.getId())));
    }

    /** Sends (or re-sends) the OTP for turning 2FA ON or OFF. */
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendOtp(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody TwoFactorDto.SendOtpRequest request) {
        twoFactorService.sendSettingsOtp(principal.getId(), request.getAction());
        return ResponseEntity.ok(ApiResponse.success("Verification code sent to your email."));
    }

    /** Google Authenticator: generates a new (not yet active) secret + QR payload for the Turn On popup. */
    @PostMapping("/authenticator/setup")
    public ResponseEntity<ApiResponse<TwoFactorDto.AuthenticatorSetup>> setupAuthenticator(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Scan the QR code with Google Authenticator.",
                twoFactorService.startAuthenticatorSetup(principal.getId())));
    }

    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<TwoFactorDto.Status>> enable(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody TwoFactorDto.OtpRequest request) {
        twoFactorService.confirmEnable(principal.getId(), request.getOtp());
        return ResponseEntity.ok(ApiResponse.success("2-step verification turned on.",
                twoFactorService.status(principal.getId())));
    }

    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<TwoFactorDto.Status>> disable(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody TwoFactorDto.OtpRequest request) {
        twoFactorService.confirmDisable(principal.getId(), request.getOtp());
        return ResponseEntity.ok(ApiResponse.success("2-step verification turned off.",
                twoFactorService.status(principal.getId())));
    }
}
