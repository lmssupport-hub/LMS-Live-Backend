package com.example.lms.controller;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.InviteDto;
import com.example.lms.service.InviteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invites")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<InviteDto.SendResponse>> send(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody InviteDto.SendRequest request) {
        InviteDto.SendResponse response = inviteService.sendInvite(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Invite sent.", response));
    }

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<InviteDto.InviteDetails>> details(@PathVariable String token) {
        return ResponseEntity.ok(
                ApiResponse.success("Invite details fetched.", inviteService.getInviteDetails(token)));
    }

    @PostMapping("/{token}/accept")
    public ResponseEntity<ApiResponse<Void>> accept(
            @PathVariable String token,
            @Valid @RequestBody InviteDto.AcceptRequest request) {
        inviteService.acceptInvite(token, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully.", null));
    }
}