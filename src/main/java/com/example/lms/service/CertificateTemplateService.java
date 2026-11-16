package com.example.lms.service;

import com.example.lms.dto.CertificateTemplateDto;
import com.example.lms.entity.CertificateTemplateEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CertificateTemplateRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The certificate design of an organization (one per company admin).
 *
 * Only the company admin designs it (the controller is ADMIN-only). It is stored per organization, so the same
 * design is what every learner's certificate will use later (see findForOrganization).
 */
@Service
public class CertificateTemplateService {

    /** Tags the designer offers - same keys as CERTIFICATE_TAGS in certificate.model.ts. */
    private static final Set<String> KNOWN_TAGS = Set.of(
            "learner_name", "course_name", "completion_date", "organization_name", "certificate_id");

    /** A certificate that does not name the learner and the course would not identify anything. */
    private static final List<String> REQUIRED_TAGS = List.of("learner_name", "course_name");

    // same pattern as TAG_PATTERN in certificate.model.ts
    private static final Pattern TAG_PATTERN = Pattern.compile("\\{\\{\\s*([a-z_]+)\\s*\\}\\}", Pattern.CASE_INSENSITIVE);

    private final CertificateTemplateRepository repository;
    private final CourseAccessService courseAccess;

    public CertificateTemplateService(CertificateTemplateRepository repository, CourseAccessService courseAccess) {
        this.repository = repository;
        this.courseAccess = courseAccess;
    }

    /** The saved design of the caller's organization, or empty when the admin has not saved one yet. */
    @Transactional(readOnly = true)
    public Optional<CertificateTemplateDto.Response> getCurrent() {
        return findForOrganization(courseAccess.currentOrganizationId())
                .map(CertificateTemplateMapper::toResponse);
    }

    /**
     * Used later when a certificate is generated for a learner: the design of the learner's organization.
     * Empty = the admin never saved one (the caller then uses the default design).
     */
    @Transactional(readOnly = true)
    public Optional<CertificateTemplateEntity> findForOrganization(Long organizationId) {
        return repository.findByOrganizationId(organizationId);
    }

    /** Creates the design the first time, replaces it afterwards (one design per organization). */
    @Transactional
    public CertificateTemplateDto.Response save(CertificateTemplateDto.SaveRequest request) {
        User admin = courseAccess.currentUser();
        Long organizationId = courseAccess.currentOrganizationId();

        validateTags(request);

        CertificateTemplateEntity entity = repository.findByOrganizationId(organizationId).orElse(null);
        if (entity == null) {
            entity = new CertificateTemplateEntity();
            entity.setOrganizationId(organizationId);
        }
        CertificateTemplateMapper.apply(request, entity);
        entity.setUpdatedBy(admin.getId());
        try {
            return CertificateTemplateMapper.toResponse(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // two saves of a brand new design at the same moment: the unique organization row already exists
            throw new ApiException("The template was saved by someone else just now. Please try again.", HttpStatus.CONFLICT);
        }
    }

    // ------------------------------------------------------------------
    // application level validation: rules that need the whole design
    // ------------------------------------------------------------------

    /** Required tags must appear somewhere; a tag-like word that is not a real tag is a typo. */
    private void validateTags(CertificateTemplateDto.SaveRequest r) {
        Set<String> used = new LinkedHashSet<>();
        Set<String> unknown = new LinkedHashSet<>();
        for (String text : List.of(r.getTitle(), r.getSubtitle(), r.getBody(), r.getFooterNote(),
                r.getDateLabel(), r.getDateText(), r.getSignatoryName(), r.getSignatoryTitle())) {
            Matcher m = TAG_PATTERN.matcher(text);
            while (m.find()) {
                String key = m.group(1).toLowerCase();
                if (KNOWN_TAGS.contains(key)) used.add(key);
                else unknown.add(m.group());
            }
        }
        if (!unknown.isEmpty()) {
            throw new ApiException("Unknown tag " + unknown.iterator().next() + ". Fix it before saving.", HttpStatus.BAD_REQUEST);
        }
        List<String> missing = REQUIRED_TAGS.stream().filter(t -> !used.contains(t)).toList();
        if (!missing.isEmpty()) {
            throw new ApiException("Add the " + String.join(" and ", missing.stream().map(CertificateTemplateService::label).toList())
                    + " tag before saving.", HttpStatus.BAD_REQUEST);
        }
    }

    private static String label(String key) {
        return switch (key) {
            case "learner_name" -> "Learner Name";
            case "course_name" -> "Course Name";
            default -> key;
        };
    }
}