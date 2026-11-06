package com.example.lms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;
import java.util.List;

public class PackageDto {

    @Data
    @NoArgsConstructor
    public static class Request {

        // S#10 - Package type (Price / Free). Replaces the old Available Package list.
        @NotBlank(message = "Please select Price or Free")
        @Pattern(
                regexp = "Price|Free",
                message = "Please select Price or Free"
        )
        private String availablePackage;

        // S#11 - Package Name (3-100 chars, letters only - no numbers or spaces; uniqueness checked in service layer)
        @NotBlank(message = "Package Name is required")
        @Size(min = 3, max = 100, message = "Package Name must be at least 3 characters")
        @Pattern(
                regexp = "^[A-Za-z]+$",
                message = "Package Name must contain only letters (no numbers or spaces)"
        )
        private String name;

        // S#12 - Package Description (optional, max 500 words)
        private String description;

        // S#13 - Price (mandatory). Free: 0. Price type: must be > 0 (see rule below).
        @NotNull(message = "Price is required")
        @PositiveOrZero(message = "Price must not be negative")
        private Double price;

        // S#14 - Billing Cycle (Monthly / Yearly for Price type, or Custom for Free only)
        @NotBlank(message = "Please select a billing cycle")
        @Pattern(
                regexp = "Monthly|Yearly|Custom",
                message = "Please select a billing cycle"
        )
        private String billingCycle;

        // Validity in days.
        //  - Free    : entered by the user (1-365)
        //  - Monthly : overwritten by the service to 30
        //  - Yearly  : overwritten by the service to 365
        @Min(value = 1, message = "Days must be at least 1")
        @Max(value = 365, message = "Days must not exceed 365")
        private Integer durationDays;

        // S#15 - User Limit (mandatory, >= 1)
        @NotNull(message = "User Limit is required")
        @Min(value = 1, message = "User Limit is required")
        private Integer userLimit;

        // S#16 - Storage Limit (optional, >= 0)
        @Min(value = 0, message = "Storage Limit must be zero or greater")
        private Integer storageLimit;

        // Checkbox shown in the UI when package type = Free
        private boolean autoAssignOnSignup;

        // Checkbox shown in the UI when package type = Price
        private boolean showOnWebsite;

        // Active / Inactive toggle in the create/edit modal.
        // Optional: null = Active on create, unchanged on update.
        @Pattern(regexp = "Active|Inactive", message = "Status must be Active or Inactive")
        private String status;

        // CHANGED: message now "Please select an item or feature"
        @NotEmpty(message = "Please select an item or feature")
        @Valid
        private List<Category> permissions;

        /**
         * Doc07 Edge Case #6 concurrency guard at the application level.
         * Null/ignored on create.
         */
        private Long version;

        @AssertTrue(message = "Package Description must not exceed 500 words")
        public boolean isDescriptionWithinWordLimit() {
            if (description == null || description.trim().isEmpty()) {
                return true;
            }
            return description.trim().split("\\s+").length <= 500;
        }

        /**
         * Free must use the "Custom" billing cycle; Price type must
         * use Monthly or Yearly.
         */
        @AssertTrue(message = "Billing cycle is not valid for the selected package")
        public boolean isBillingCycleValidForPackage() {
            if (availablePackage == null || billingCycle == null) {
                return true; // field-level @NotBlank reports these
            }
            if ("Free".equals(availablePackage)) {
                return "Custom".equals(billingCycle);
            }
            return "Monthly".equals(billingCycle) || "Yearly".equals(billingCycle);
        }

        /** Days are mandatory only for Free. */
        @AssertTrue(message = "Please enter the number of days")
        public boolean isTrialDaysProvided() {
            if (!"Free".equals(availablePackage)) {
                return true;
            }
            return durationDays != null && durationDays >= 1;
        }

        /** Only Free may have zero price; a Price package must have a price above zero. */
        @AssertTrue(message = "Price must be greater than zero")
        public boolean isPriceValidForPackage() {
            if (availablePackage == null || price == null) return true; // field-level checks report these
            if ("Free".equals(availablePackage)) return true;
            return price > 0;
        }

        // CHANGED: message now "Please select an item or feature"
        @AssertTrue(message = "Please select an item or feature")
        public boolean isAtLeastOnePermissionEnabled() {
            if (permissions == null || permissions.isEmpty()) {
                return false;
            }
            return permissions.stream()
                    .filter(cat -> cat.getFeatures() != null)
                    .flatMap(cat -> cat.getFeatures().stream())
                    .filter(feature -> feature.getPermissions() != null)
                    .anyMatch(feature -> {
                        Permission p = feature.getPermissions();
                        return p.isCreate() || p.isRead() || p.isUpdate() || p.isDelete();
                    });
        }
    }

    /** Body of PATCH /api/packages/{id}/status. */
    @Data
    @NoArgsConstructor
    public static class StatusRequest {

        @NotBlank(message = "Please select a status")
        @Pattern(
                regexp = "Active|Inactive",
                message = "Status must be Active or Inactive"
        )
        private String status;
    }

    @Data
    @NoArgsConstructor
    public static class Response {
        private Long id;
        private String availablePackage;
        private String name;
        private String description;
        private Double price;
        private String billingCycle;
        private Integer durationDays;
        private Integer userLimit;
        private Integer storageLimit;
        private boolean autoAssignOnSignup;
        private boolean showOnWebsite;
        private String status;
        private LocalDateTime createdAt;
        private List<Category> permissions;
        private Long version;
    }

    @Data
    @NoArgsConstructor
    public static class Category {

        @NotBlank(message = "Please select a package")
        private String id;

        private String name;
        private boolean enabled;

        @Valid
        private List<Feature> features;
    }

    @Data
    @NoArgsConstructor
    public static class Feature {

        @NotBlank(message = "Please select a package")
        private String id;

        private String name;
        private Permission permissions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Permission {
        private boolean create;
        private boolean read;
        private boolean update;
        private boolean delete;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureCatalogEntry {
        private String categoryId;
        private String categoryName;
        private List<Feature> features;
    }

    /** One row of "Assigned admins": who has the package, since when, and until when. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminAssignment {
        private Long userId;
        private LocalDateTime assignedAt;
        private LocalDateTime expiresAt;
    }

    /**
     * Billing form from the Payment page. Stored for display only (Manage Details ->
     * Billing Details); it is never used to decide what is charged.
     */
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

        @Size(max = 50, message = "VAT number is too long")
        private String vatNumber;
    }

    /**
     * Body of POST /api/packages/purchase (company admin buys a package for THEMSELVES).
     * The user is taken from the JWT, never from this body, so nobody can buy for someone else.
     * Card data is only validated, never stored or logged.
     * TEMP: until a real gateway (Stripe etc.) is connected, the service checks these against a test card.
     */
    @Data
    @NoArgsConstructor
    public static class PurchaseRequest {

        @NotNull(message = "Please select a package")
        @Positive(message = "Please select a package")
        private Long packageId;

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

        /** NEW: billing form values, saved so Manage Details can show them later. */
        @NotNull(message = "Billing details are required")
        @Valid
        private BillingRequest billing;
    }

    /** Logged-in admin's own package countdown (header "days left" pill). packageId/expiresAt null = no package. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MySubscription {
        private Long packageId;
        private String packageName;
        private LocalDateTime assignedAt;
        private LocalDateTime expiresAt;
        /** Server clock at response time, so the browser can count down without depending on its own timezone. */
        private LocalDateTime serverTime;
    }

    /** NEW: response of GET /api/packages/my-billing. All fields null = nothing saved yet. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MyBilling {
        private String billingName;
        private String companyName;
        private String email;
        private String address;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private String vatNumber;
    }
}