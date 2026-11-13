package com.example.lms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Request / response bodies of the learner "pay for a course" flow (separate from the package payment). */
public class CoursePaymentDto {

    /** Billing form of the course Payment page. Stored for display only. */
    @Data
    @NoArgsConstructor
    public static class BillingRequest {

        @Size(max = 100, message = "First name is too long")
        private String firstName;

        @Size(max = 100, message = "Last name is too long")
        private String lastName;

        @Size(max = 200, message = "Company name is too long")
        private String companyName;

        @NotBlank(message = "Billing email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 100, message = "Enter a valid email address")
        private String email;

        @NotBlank(message = "Billing address is required")
        @Size(max = 200, message = "Billing address is too long")
        private String address;

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City is too long")
        private String city;

        @NotBlank(message = "Postal code is required")
        @Size(max = 100, message = "Postal code is too long")
        private String postalCode;

        @Size(max = 100, message = "State is too long")
        private String state;

        @NotBlank(message = "Country is required")
        @Size(max = 100, message = "Country is too long")
        private String country;
    }

    /**
     * Body of POST /api/courses/{courseId}/purchase.
     * The course comes from the URL and the price from the database - the browser sends neither.
     */
    @Data
    @NoArgsConstructor
    public static class PurchaseRequest {

        @NotBlank(message = "Card number is required")
        @Pattern(regexp = "^[0-9 ]{13,23}$", message = "Enter a valid card number")
        private String cardNumber;

        @NotBlank(message = "Expiry date is required")
        @Pattern(regexp = "^(0[1-9]|1[0-2])/[0-9]{2}$", message = "Enter the expiry date as MM/YY")
        private String expiry;

        @NotBlank(message = "CVC is required")
        @Pattern(regexp = "^[0-9]{3,4}$", message = "Enter a 3 or 4 digit CVC")
        private String cvc;

        @NotBlank(message = "Verification code is required")
        @Pattern(regexp = "^[0-9]{4,8}$", message = "Enter a code containing 4 to 8 digits")
        private String otp;

        @NotNull(message = "Billing details are required")
        @Valid
        private BillingRequest billing;
    }

    /** What the Payment page shows (Order Summary). Real price straight from the database. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckoutResponse {
        private Long courseId;
        private String courseName;
        private String description;
        private String thumbnailUrl;
        private BigDecimal price;
        private boolean alreadyEnrolled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseResponse {
        private Long courseId;
        private String courseName;
        private BigDecimal amount;
        private LocalDateTime purchasedAt;
        private String enrollmentStatus;
    }
}