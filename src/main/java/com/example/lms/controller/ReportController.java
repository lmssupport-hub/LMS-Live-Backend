package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.ReportDto;
import com.example.lms.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin > Reports. Each report is ONE request that returns finished numbers.
 * Who may open them (course managers only) and tenant scoping are enforced in ReportService.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** GET /api/reports/courses */
    @GetMapping("/courses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ReportDto.CourseRow>>> courseReport() {
        return ResponseEntity.ok(ApiResponse.success(
                "Course report fetched successfully.", reportService.getCourseReport()));
    }

    /** GET /api/reports/learners */
    @GetMapping("/learners")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ReportDto.LearnerRow>>> learnerReport() {
        return ResponseEntity.ok(ApiResponse.success(
                "Learner report fetched successfully.", reportService.getLearnerReport()));
    }
}