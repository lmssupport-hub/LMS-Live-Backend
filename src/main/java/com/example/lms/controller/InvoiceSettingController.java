package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.InvoiceSettingDto;
import com.example.lms.service.InvoiceSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Profile > Settings > Invoice Setup of the caller's organization. Company admin only.
 */
@RestController
@RequestMapping("/api/invoice-settings")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class InvoiceSettingController {

    private final InvoiceSettingService service;

    public InvoiceSettingController(InvoiceSettingService service) {
        this.service = service;
    }

    /** The saved setup, or data = null when none was saved yet (the page then shows its defaults). */
    @GetMapping
    @Operation(summary = "Get the invoice setup of my organization (data is null until one is saved)")
    public ResponseEntity<ApiResponse<InvoiceSettingDto.Response>> get() {
        return ResponseEntity.ok(ApiResponse.success("Invoice setup fetched successfully.",
                service.getCurrent().orElse(null)));
    }

    /** Save (create or replace) the setup. */
    @PutMapping
    @Operation(summary = "Save the invoice setup of my organization")
    public ResponseEntity<ApiResponse<InvoiceSettingDto.Response>> save(
            @Valid @RequestBody InvoiceSettingDto.SaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Invoice setup saved successfully.", service.save(request)));
    }
}