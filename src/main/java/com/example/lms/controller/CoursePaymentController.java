package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.CoursePaymentDto;
import com.example.lms.service.CoursePaymentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Learner pays for a paid course. Not the package payment - that one is /api/packages/purchase
 * (company admin). Access rules (own organization, published, permission to view the Course List)
 * are checked inside CoursePaymentService.
 */
@RestController
@RequestMapping("/api/courses/{courseId}")
@SecurityRequirement(name = "bearerAuth")
public class CoursePaymentController {

    private final CoursePaymentService coursePaymentService;

    public CoursePaymentController(CoursePaymentService coursePaymentService) {
        this.coursePaymentService = coursePaymentService;
    }

    /** Order Summary of the Payment page. */
    @GetMapping("/checkout")
    public ResponseEntity<ApiResponse<CoursePaymentDto.CheckoutResponse>> checkout(@PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Checkout fetched successfully.", coursePaymentService.getCheckout(courseId)));
    }

    /** "Pay" button: verifies the payment, then enrolls the learner. */
    @PostMapping("/purchase")
    public ResponseEntity<ApiResponse<CoursePaymentDto.PurchaseResponse>> purchase(
            @PathVariable Long courseId,
            @Valid @RequestBody CoursePaymentDto.PurchaseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Payment successful. You are now enrolled.", coursePaymentService.purchase(courseId, request)));
    }
}