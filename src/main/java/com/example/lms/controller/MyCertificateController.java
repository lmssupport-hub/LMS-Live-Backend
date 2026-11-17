package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.MyCertificateDto;
import com.example.lms.service.IssuedCertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** "My Certificate" of the logged-in learner: the certificates earned by completing courses. Learners only. */
@RestController
@RequestMapping("/api/my-certificates")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('LEARNER')")
public class MyCertificateController {

    private final IssuedCertificateService service;

    public MyCertificateController(IssuedCertificateService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "My earned certificates (Course Name, Issue date)")
    public ResponseEntity<ApiResponse<List<MyCertificateDto.ListItem>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Certificates fetched successfully.", service.listMine()));
    }

    @GetMapping("/{certificateId}")
    @Operation(summary = "One of my certificates with the design to draw it with")
    public ResponseEntity<ApiResponse<MyCertificateDto.Detail>> get(@PathVariable Long certificateId) {
        return ResponseEntity.ok(ApiResponse.success("Certificate fetched successfully.", service.getMine(certificateId)));
    }
}