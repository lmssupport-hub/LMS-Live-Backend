package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.SectionProgressDto;
import com.example.lms.service.SectionProgressService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Learner module progress. Access rules (own organization, enrolled learner) are checked in the service;
 * admins / instructors just get tracked = false.
 */
@RestController
@RequestMapping("/api/courses/{courseId}")
@SecurityRequirement(name = "bearerAuth")
public class SectionProgressController {

    private final SectionProgressService progressService;

    public SectionProgressController(SectionProgressService progressService) {
        this.progressService = progressService;
    }

    /** Rings on the Course Preview page. */
    @GetMapping("/progress")
    public ResponseEntity<ApiResponse<SectionProgressDto.Response>> getProgress(@PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.success("Progress fetched successfully.", progressService.getProgress(courseId)));
    }

    /** Module opened (IN_PROGRESS) or "Mark as completed" (COMPLETED). */
    @PostMapping("/sections/{sectionId}/progress")
    public ResponseEntity<ApiResponse<SectionProgressDto.Response>> update(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @Valid @RequestBody SectionProgressDto.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Progress saved.",
                progressService.update(courseId, sectionId, request.getStatus(), request.getPercent())));
    }
}