package com.example.lms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API shape of the certificate design. Field names match the Angular `CertificateTemplate` model exactly.
 * Request level rules (size, format) live here; the rules that need the whole design (required / unknown tags)
 * are checked in CertificateTemplateService.
 */
public class CertificateTemplateDto {

    private static final String HEX = "^#[0-9a-fA-F]{6}$";
    /** Images arrive as data URLs from the designer (png / jpeg / webp / svg), at most 2 MB each before encoding. */
    private static final String IMAGE_DATA_URL = "^data:image/(png|jpeg|webp|svg\\+xml);base64,[A-Za-z0-9+/=]+$";

    /** Body of PUT /api/certificate-template. */
    @Data
    @NoArgsConstructor
    public static class SaveRequest {

        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must be at most 200 characters.")
        private String title;

        @NotBlank(message = "Subtitle is required.")
        @Size(max = 200, message = "Subtitle must be at most 200 characters.")
        private String subtitle;

        @NotBlank(message = "Main text is required.")
        @Size(max = 2000, message = "Main text must be at most 2000 characters.")
        private String body;

        @NotBlank(message = "Footer note is required.")
        @Size(max = 300, message = "Footer note must be at most 300 characters.")
        private String footerNote;

        @NotBlank(message = "Date caption is required.")
        @Size(max = 100, message = "Date caption must be at most 100 characters.")
        private String dateLabel;

        @NotBlank(message = "Date is required.")
        @Size(max = 200, message = "Date must be at most 200 characters.")
        private String dateText;

        @NotBlank(message = "Signatory name is required.")
        @Size(max = 150, message = "Signatory name must be at most 150 characters.")
        private String signatoryName;

        @NotBlank(message = "Signatory designation is required.")
        @Size(max = 200, message = "Signatory designation must be at most 200 characters.")
        private String signatoryTitle;

        @NotBlank(message = "Accent colour is required.")
        @Pattern(regexp = HEX, message = "Accent colour must be a hex colour such as #2f7684.")
        private String accent;

        @NotBlank(message = "Background colour is required.")
        @Pattern(regexp = HEX, message = "Background colour must be a hex colour such as #ffffff.")
        private String background;

        @NotBlank(message = "Font is required.")
        @Pattern(regexp = "^(classic|elegant|modern)$", message = "Font must be classic, elegant or modern.")
        private String font;

        @NotBlank(message = "Border is required.")
        @Pattern(regexp = "^(double|single|none)$", message = "Border must be double, single or none.")
        private String border;

        @NotBlank(message = "Logo position is required.")
        @Pattern(regexp = "^(left|center|right)$", message = "Logo position must be left, center or right.")
        private String logoPosition;

        /** null = no picture. */
        @Size(max = 3_000_000, message = "The background image is too large. Use a smaller image.")
        @Pattern(regexp = IMAGE_DATA_URL, message = "The background image must be a PNG, JPG, WebP or SVG image.")
        private String backgroundImage;

        @Size(max = 3_000_000, message = "The logo is too large. Use a smaller image.")
        @Pattern(regexp = IMAGE_DATA_URL, message = "The logo must be a PNG, JPG, WebP or SVG image.")
        private String logo;

        @Size(max = 3_000_000, message = "The signature is too large. Use a smaller image.")
        @Pattern(regexp = IMAGE_DATA_URL, message = "The signature must be a PNG, JPG, WebP or SVG image.")
        private String signature;
    }

    /** The saved design. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String title;
        private String subtitle;
        private String body;
        private String footerNote;
        private String dateLabel;
        private String dateText;
        private String signatoryName;
        private String signatoryTitle;
        private String accent;
        private String background;
        private String font;
        private String border;
        private String logoPosition;
        private String backgroundImage;
        private String logo;
        private String signature;
        private LocalDateTime updatedAt;
    }
}