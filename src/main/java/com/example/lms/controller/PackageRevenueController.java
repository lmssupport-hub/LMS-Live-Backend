package com.example.lms.controller;

import com.example.lms.dto.PackageRevenueDto;
import com.example.lms.service.PackageRevenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/packages/revenue")
@RequiredArgsConstructor
public class PackageRevenueController {

    private final PackageRevenueService revenueService;

    /** GET /api/packages/revenue[?from=yyyy-MM-dd&to=yyyy-MM-dd] - Super Admin dashboard revenue chart. */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<PackageRevenueDto.Point>> revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(revenueService.getDailyRevenue(from, to));
    }
}