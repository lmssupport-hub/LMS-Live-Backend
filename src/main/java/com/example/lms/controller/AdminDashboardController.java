package com.example.lms.controller;

import com.example.lms.dto.AdminDashboardDto;
import com.example.lms.dto.ApiResponse;
import com.example.lms.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin dashboard home. Every logged-in non-learner sees the numbers of their own organization. */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    /** GET /api/dashboard/admin?period=WEEK|MONTH|YEAR (default MONTH). */
    @GetMapping("/admin")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AdminDashboardDto.Response>> adminDashboard(
            @RequestParam(defaultValue = "MONTH") String period) {
        return ResponseEntity.ok(ApiResponse.success(
                "Dashboard fetched successfully.", dashboardService.getDashboard(period)));
    }
}