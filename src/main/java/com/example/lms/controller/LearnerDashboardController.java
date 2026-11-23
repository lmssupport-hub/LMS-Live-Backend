package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.LearnerDashboardDto;
import com.example.lms.service.LearnerDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Learner dashboard home. Always the numbers of the logged-in user only. */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class LearnerDashboardController {

    private final LearnerDashboardService dashboardService;

    /** GET /api/dashboard/learner */
    @GetMapping("/learner")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LearnerDashboardDto.Response>> learnerDashboard() {
        return ResponseEntity.ok(ApiResponse.success(
                "Dashboard fetched successfully.", dashboardService.getDashboard()));
    }
}