package com.example.lms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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

        // S#11 - Package Name (3-100 chars, unique - uniqueness checked in service layer)
        @NotBlank(message = "Package Name is required")
        @Size(min = 3, max = 100, message = "Package Name must be at least 3 characters")
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

        @NotEmpty(message = "Please configure the required permissions before saving")
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

        @AssertTrue(message = "Please configure the required permissions before saving")
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
}