package com.example.lms.service;

import com.example.lms.dto.CertificateTemplateDto;
import com.example.lms.entity.CertificateTemplateEntity;

/** Explicit, field-by-field mapping between the API shape and the table (no reflection / implicit mapping). */
public final class CertificateTemplateMapper {

    private CertificateTemplateMapper() {
    }

    /** Copies every design field of the request onto the entity (new or existing). */
    public static void apply(CertificateTemplateDto.SaveRequest request, CertificateTemplateEntity entity) {
        entity.setTitle(request.getTitle().trim());
        entity.setSubtitle(request.getSubtitle().trim());
        entity.setBody(request.getBody().trim());
        entity.setFooterNote(request.getFooterNote().trim());
        entity.setDateLabel(request.getDateLabel().trim());
        entity.setDateText(request.getDateText().trim());
        entity.setSignatoryName(request.getSignatoryName().trim());
        entity.setSignatoryTitle(request.getSignatoryTitle().trim());
        entity.setAccent(request.getAccent().toLowerCase());
        entity.setBackground(request.getBackground().toLowerCase());
        entity.setFont(request.getFont());
        entity.setBorder(request.getBorder());
        entity.setLogoPosition(request.getLogoPosition());
        entity.setBackgroundImage(blankToNull(request.getBackgroundImage()));
        entity.setLogo(blankToNull(request.getLogo()));
        entity.setSignature(blankToNull(request.getSignature()));
    }

    public static CertificateTemplateDto.Response toResponse(CertificateTemplateEntity e) {
        return CertificateTemplateDto.Response.builder()
                .title(e.getTitle())
                .subtitle(e.getSubtitle())
                .body(e.getBody())
                .footerNote(e.getFooterNote())
                .dateLabel(e.getDateLabel())
                .dateText(e.getDateText())
                .signatoryName(e.getSignatoryName())
                .signatoryTitle(e.getSignatoryTitle())
                .accent(e.getAccent())
                .background(e.getBackground())
                .font(e.getFont())
                .border(e.getBorder())
                .logoPosition(e.getLogoPosition())
                .backgroundImage(e.getBackgroundImage())
                .logo(e.getLogo())
                .signature(e.getSignature())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}