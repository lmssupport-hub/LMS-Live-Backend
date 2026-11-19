package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.CertificateTemplateDto;
import com.example.lms.service.CertificateTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * The certificate design of the caller's organization. Company admin only (the "Certificate" menu is admin-only);
 * learners will get their generated certificate from a separate endpoint later.
 */
@RestController
@RequestMapping("/api/certificate-template")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
public class CertificateTemplateController {

    private final CertificateTemplateService service;

    public CertificateTemplateController(CertificateTemplateService service) {
        this.service = service;
    }

    /** The saved design, or data = null when none was saved yet (the designer then shows its default design). */
    @GetMapping
    @PreAuthorize("@perm.can(authentication, 'CERTIFICATE_MGMT_TEMPLATE', 'read')")
    @Operation(summary = "Get the certificate design of my organization (data is null until one is saved)")
    public ResponseEntity<ApiResponse<CertificateTemplateDto.Response>> get() {
        return ResponseEntity.ok(ApiResponse.success("Certificate template fetched successfully.",
                service.getCurrent().orElse(null)));
    }

    /** Save (create or replace) the design. */
    @PutMapping
    @PreAuthorize("@perm.can(authentication, 'CERTIFICATE_MGMT_TEMPLATE', 'update')")
    @Operation(summary = "Save the certificate design of my organization")
    public ResponseEntity<ApiResponse<CertificateTemplateDto.Response>> save(
            @Valid @RequestBody CertificateTemplateDto.SaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Certificate template saved successfully.", service.save(request)));
    }
}