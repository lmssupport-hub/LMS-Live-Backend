package com.example.lms.service;

import com.example.lms.dto.EbookDto;
import com.example.lms.entity.EbookEntity;
import com.example.lms.entity.SectionEntity;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.EbookRepository;
import com.example.lms.repository.SectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ebooks of a course section (Add Activity -> Ebook).
 * Managing (create / edit / delete) is for admins and instructors; reading follows the same rule as multimedia:
 * own organization and (manager, or a learner enrolled in the PUBLISHED course).
 */
@Service
public class EbookService {

    // Same rule as the multimedia Resource Name: required, 4..100.
    private static final int MIN_NAME_CHARS = 4;
    private static final int MAX_NAME_CHARS = 100;

    // Only used to check that the content is a JSON array. Created here (not injected): Spring Boot 4 auto-configures
    // Jackson 3 (tools.jackson), so there is no com.fasterxml ObjectMapper bean to inject.
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Set<String> MANAGE_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "INSTRUCTOR");

    /** Images are stored inside the content (data URLs), so the limit is generous. */
    @Value("${app.ebook.max-content-mb:25}")
    private long maxContentMb;

    private final EbookRepository ebookRepository;
    private final SectionRepository sectionRepository;
    private final CourseAccessService courseAccess;

    public EbookService(EbookRepository ebookRepository,
                        SectionRepository sectionRepository,
                        CourseAccessService courseAccess) {
        this.ebookRepository = ebookRepository;
        this.sectionRepository = sectionRepository;
        this.courseAccess = courseAccess;
    }

    // ---------- create ----------

    @Transactional
    public EbookDto.EbookResponse create(Long courseId, Long sectionId, EbookDto.EbookRequest request) {
        requireManageRole();
        SectionEntity section = getSectionOrThrow(courseId, sectionId);

        String name = validateName(request == null ? null : request.getName());
        String content = validateContent(request == null ? null : request.getContent());

        // Also protects against a double click on Save: the second request hits this check.
        if (ebookRepository.existsBySectionIdAndNameIgnoreCase(sectionId, name)) {
            throw new ApiException("An ebook with the same name already exists in this section.", HttpStatus.CONFLICT);
        }
        EbookEntity entity = EbookEntity.builder().section(section).name(name).content(content).build();
        try {
            return toResponse(ebookRepository.saveAndFlush(entity), courseId, sectionId);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException("An ebook with the same name already exists in this section.", HttpStatus.CONFLICT);
        }
    }

    // ---------- read ----------

    /** Rows for Organize Modules and the content tabs (no content). */
    @Transactional(readOnly = true)
    public List<EbookDto.EbookSummaryResponse> list(Long courseId, Long sectionId) {
        getVisibleSectionOrThrow(courseId, sectionId);
        return ebookRepository.findSummariesBySectionIdOrderByCreatedAtAsc(sectionId).stream()
                .map(e -> EbookDto.EbookSummaryResponse.builder()
                        .id(e.getId()).courseId(courseId).sectionId(sectionId)
                        .name(e.getName()).createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public EbookDto.EbookResponse get(Long courseId, Long sectionId, Long ebookId) {
        getVisibleSectionOrThrow(courseId, sectionId);
        return toResponse(getEbookOrThrow(sectionId, ebookId), courseId, sectionId);
    }

    // ---------- update ----------

    @Transactional
    public EbookDto.EbookResponse update(Long courseId, Long sectionId, Long ebookId, EbookDto.EbookRequest request) {
        requireManageRole();
        getSectionOrThrow(courseId, sectionId);
        EbookEntity entity = getEbookOrThrow(sectionId, ebookId);

        String name = validateName(request == null ? null : request.getName());
        String content = validateContent(request == null ? null : request.getContent());
        if (ebookRepository.existsBySectionIdAndNameIgnoreCaseAndIdNot(sectionId, name, ebookId)) {
            throw new ApiException("An ebook with the same name already exists in this section.", HttpStatus.CONFLICT);
        }
        entity.setName(name);
        entity.setContent(content);
        try {
            return toResponse(ebookRepository.saveAndFlush(entity), courseId, sectionId);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException("An ebook with the same name already exists in this section.", HttpStatus.CONFLICT);
        }
    }

    // ---------- delete ----------

    @Transactional
    public void delete(Long courseId, Long sectionId, Long ebookId) {
        requireManageRole();
        getSectionOrThrow(courseId, sectionId);
        ebookRepository.delete(getEbookOrThrow(sectionId, ebookId));
    }

    // ---------- validation ----------

    private String validateName(String rawName) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) throw new ApiException("Ebook Name is required.", HttpStatus.BAD_REQUEST);
        if (name.length() < MIN_NAME_CHARS) throw new ApiException("Minimum 4 characters.", HttpStatus.BAD_REQUEST);
        if (name.length() > MAX_NAME_CHARS) throw new ApiException("Maximum 100 characters.", HttpStatus.BAD_REQUEST);
        return name;
    }

    /** The content must be a JSON array with at least one section, and not bigger than the configured limit. */
    private String validateContent(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            throw new ApiException("Add at least one section to the ebook.", HttpStatus.BAD_REQUEST);
        }
        long bytes = rawContent.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > maxContentMb * 1024 * 1024) {
            throw new ApiException("The ebook is too large (maximum " + maxContentMb + " MB). Use smaller images.",
                    HttpStatus.PAYLOAD_TOO_LARGE);
        }
        JsonNode root;
        try {
            root = JSON.readTree(rawContent);
        } catch (Exception e) {
            throw new ApiException("The ebook content is not valid.", HttpStatus.BAD_REQUEST);
        }
        if (root == null || !root.isArray() || root.isEmpty()) {
            throw new ApiException("Add at least one section to the ebook.", HttpStatus.BAD_REQUEST);
        }
        for (JsonNode section : root) {
            if (!section.isObject() || !section.path("blocks").isArray()) {
                throw new ApiException("The ebook content is not valid.", HttpStatus.BAD_REQUEST);
            }
        }
        return rawContent;
    }

    // ---------- access ----------

    private boolean canManage() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replaceFirst("(?i)^ROLE_", "").toUpperCase(Locale.ROOT))
                .anyMatch(MANAGE_ROLES::contains);
    }

    private void requireManageRole() {
        if (!canManage()) {
            throw new ApiException("Access Denied", HttpStatus.FORBIDDEN);
        }
    }

    private SectionEntity getSectionOrThrow(Long courseId, Long sectionId) {
        courseAccess.requireCourseInOrganization(courseId); // own organization only
        return sectionRepository.findByIdAndCourseId(sectionId, courseId)
                .orElseThrow(() -> new ApiException(
                        "Section not found with id: " + sectionId + " in course: " + courseId, HttpStatus.NOT_FOUND));
    }

    // reading = own organization AND (manager, or a learner enrolled in the PUBLISHED course)
    private SectionEntity getVisibleSectionOrThrow(Long courseId, Long sectionId) {
        courseAccess.requireReadableCourse(courseId);
        return getSectionOrThrow(courseId, sectionId);
    }

    private EbookEntity getEbookOrThrow(Long sectionId, Long ebookId) {
        return ebookRepository.findByIdAndSectionId(ebookId, sectionId)
                .orElseThrow(() -> new ApiException(
                        "Ebook not found with id: " + ebookId + " in section: " + sectionId, HttpStatus.NOT_FOUND));
    }

    private EbookDto.EbookResponse toResponse(EbookEntity e, Long courseId, Long sectionId) {
        return EbookDto.EbookResponse.builder()
                .id(e.getId()).courseId(courseId).sectionId(sectionId)
                .name(e.getName()).content(e.getContent())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }
}