package com.example.lms.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.lms.dto.MultimediaDto;
import com.example.lms.entity.MultimediaEntity;
import com.example.lms.entity.SectionEntity;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.MultimediaRepository;
import com.example.lms.repository.SectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MultimediaService {

    private static final Logger log = LoggerFactory.getLogger(MultimediaService.class);

    // SRS Field List: Resource Name 4..100, Description max 500.
    private static final int MIN_NAME_CHARS = 4;
    private static final int MAX_NAME_CHARS = 100;
    private static final int MAX_DESCRIPTION_CHARS = 500;

    // SRS Permissions: only Administrators and Instructors can upload / manage.
    private static final Set<String> MANAGE_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "INSTRUCTOR");

    private static final Map<String, String> EXTENSION_TO_TYPE = Map.ofEntries(
            Map.entry("mp4", "VIDEO"),
            Map.entry("mp3", "AUDIO"),
            Map.entry("pdf", "PDF"),
            Map.entry("docx", "DOCUMENT"),
            Map.entry("pptx", "PRESENTATION"),
            Map.entry("xlsx", "SPREADSHEET"),
            Map.entry("jpg", "IMAGE"),
            Map.entry("jpeg", "IMAGE"),
            Map.entry("png", "IMAGE"),
            Map.entry("zip", "ARCHIVE")
    );

    // SRS Defaults: file types and max size are configurable by the system administrator.
    @Value("${app.multimedia.allowed-extensions:mp4,mp3,pdf,docx,pptx,xlsx,jpg,jpeg,png,zip}")
    private List<String> allowedExtensions;

    @Value("${app.multimedia.max-file-size-mb:50}")
    private long maxFileSizeMb;

    private final MultimediaRepository multimediaRepository;
    private final SectionRepository sectionRepository;
    private final Cloudinary cloudinary;

    public MultimediaService(MultimediaRepository multimediaRepository,
                             SectionRepository sectionRepository,
                             Cloudinary cloudinary) {
        this.multimediaRepository = multimediaRepository;
        this.sectionRepository = sectionRepository;
        this.cloudinary = cloudinary;
    }

    private record StoredFile(String url, String publicId, String resourceType) {
    }

    // ---------- create ----------

    @Transactional
    public MultimediaDto.MultimediaResponse createResource(Long courseId, Long sectionId,
                                                           MultimediaDto.MultimediaRequest request,
                                                           MultipartFile file) {
        requireManageRole();
        SectionEntity section = getSectionOrThrow(courseId, sectionId);

        String name = validateName(request == null ? null : request.getName());
        String description = validateDescription(request == null ? null : request.getDescription());
        String extension = validateFile(file);

        // Also protects against double-click on Save (SRS Edge Case #1): the second request hits this check.
        if (multimediaRepository.existsBySectionIdAndNameIgnoreCase(sectionId, name)) {
            throw new ApiException("A resource with the same name already exists in this section.", HttpStatus.CONFLICT);
        }

        StoredFile stored = uploadToStorage(file, courseId, sectionId);

        MultimediaEntity entity = MultimediaEntity.builder()
                .section(section)
                .name(name)
                .description(description)
                .resourceType(EXTENSION_TO_TYPE.getOrDefault(extension, "OTHER"))
                .fileUrl(stored.url())
                .storagePublicId(stored.publicId())
                .storageResourceType(stored.resourceType())
                .originalFileName(file.getOriginalFilename())
                .fileExtension(extension)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .build();

        try {
            return toResponse(multimediaRepository.saveAndFlush(entity));
        } catch (RuntimeException e) {
            // No incomplete uploads (SRS Edge Case #5): don't leave an orphan file in storage.
            deleteFromStorage(stored.publicId(), stored.resourceType());
            throw e;
        }
    }

    // ---------- read ----------

    @Transactional(readOnly = true)
    public List<MultimediaDto.MultimediaResponse> getAllResources(Long courseId, Long sectionId) {
        getVisibleSectionOrThrow(courseId, sectionId);
        return multimediaRepository.findBySectionIdOrderByCreatedAtAsc(sectionId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MultimediaDto.MultimediaResponse getResourceById(Long courseId, Long sectionId, Long resourceId) {
        getVisibleSectionOrThrow(courseId, sectionId);
        return toResponse(getResourceOrThrow(sectionId, resourceId));
    }

    // ---------- update ----------

    @Transactional
    public MultimediaDto.MultimediaResponse updateResource(Long courseId, Long sectionId, Long resourceId,
                                                           MultimediaDto.MultimediaRequest request,
                                                           MultipartFile file) {
        requireManageRole();
        getSectionOrThrow(courseId, sectionId);
        MultimediaEntity entity = getResourceOrThrow(sectionId, resourceId);

        String name = validateName(request == null ? null : request.getName());
        String description = validateDescription(request == null ? null : request.getDescription());

        if (multimediaRepository.existsBySectionIdAndNameIgnoreCaseAndIdNot(sectionId, name, resourceId)) {
            throw new ApiException("A resource with the same name already exists in this section.", HttpStatus.CONFLICT);
        }

        entity.setName(name);
        entity.setDescription(description);

        StoredFile newFile = null;
        String oldPublicId = entity.getStoragePublicId();
        String oldStorageType = entity.getStorageResourceType();

        // File is optional on edit: only replaced when a new one is sent.
        if (file != null && !file.isEmpty()) {
            String extension = validateFile(file);
            newFile = uploadToStorage(file, courseId, sectionId);
            entity.setResourceType(EXTENSION_TO_TYPE.getOrDefault(extension, "OTHER"));
            entity.setFileUrl(newFile.url());
            entity.setStoragePublicId(newFile.publicId());
            entity.setStorageResourceType(newFile.resourceType());
            entity.setOriginalFileName(file.getOriginalFilename());
            entity.setFileExtension(extension);
            entity.setContentType(file.getContentType());
            entity.setFileSize(file.getSize());
        }

        MultimediaEntity saved;
        try {
            saved = multimediaRepository.saveAndFlush(entity);
        } catch (RuntimeException e) {
            if (newFile != null) deleteFromStorage(newFile.publicId(), newFile.resourceType());
            throw e;
        }
        if (newFile != null) deleteFromStorage(oldPublicId, oldStorageType);
        return toResponse(saved);
    }

    // ---------- delete ----------

    @Transactional
    public void deleteResource(Long courseId, Long sectionId, Long resourceId) {
        requireManageRole();
        getSectionOrThrow(courseId, sectionId);
        MultimediaEntity entity = getResourceOrThrow(sectionId, resourceId);
        String publicId = entity.getStoragePublicId();
        String storageType = entity.getStorageResourceType();
        multimediaRepository.delete(entity);
        deleteFromStorage(publicId, storageType);
    }

    // ---------- validation ----------

    private String validateName(String rawName) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) throw new ApiException("Resource Name is required.", HttpStatus.BAD_REQUEST);
        if (name.length() < MIN_NAME_CHARS) throw new ApiException("Minimum 4 characters.", HttpStatus.BAD_REQUEST);
        if (name.length() > MAX_NAME_CHARS) throw new ApiException("Maximum 100 characters.", HttpStatus.BAD_REQUEST);
        return name;
    }

    private String validateDescription(String rawDescription) {
        if (rawDescription == null) return null;
        String description = rawDescription.trim();
        if (description.isEmpty()) return null;
        if (description.length() > MAX_DESCRIPTION_CHARS) {
            throw new ApiException("Description exceeds maximum length.", HttpStatus.BAD_REQUEST);
        }
        return description;
    }

    /** Returns the lower-case file extension if the file is valid; throws the SRS error otherwise. */
    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("File is required.", HttpStatus.BAD_REQUEST);
        }
        String extension = extractExtension(file.getOriginalFilename());
        boolean allowed = allowedExtensions.stream()
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .anyMatch(e -> e.equals(extension));
        if (extension.isEmpty() || !allowed) {
            throw new ApiException("Unsupported file format.", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > maxFileSizeMb * 1024 * 1024) {
            throw new ApiException("File size exceeds maximum limit.", HttpStatus.BAD_REQUEST);
        }
        return extension;
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
    }

    // ---------- permissions ----------

    private boolean canManage() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replaceFirst("(?i)^ROLE_", "").toUpperCase(Locale.ROOT))
                .anyMatch(MANAGE_ROLES::contains);
    }

    // SRS Edge Case #8
    private void requireManageRole() {
        if (!canManage()) {
            throw new ApiException("Access Denied", HttpStatus.FORBIDDEN);
        }
    }

    // ---------- lookups ----------

    private SectionEntity getSectionOrThrow(Long courseId, Long sectionId) {
        return sectionRepository.findByIdAndCourseId(sectionId, courseId)
                .orElseThrow(() -> new ApiException(
                        "Section not found with id: " + sectionId + " in course: " + courseId, HttpStatus.NOT_FOUND));
    }

    // Learners only see resources of published courses (SRS: "published or assigned resources").
    private SectionEntity getVisibleSectionOrThrow(Long courseId, Long sectionId) {
        SectionEntity section = getSectionOrThrow(courseId, sectionId);
        if (!canManage() && !"PUBLISHED".equalsIgnoreCase(section.getCourse().getStatus())) {
            throw new ApiException("Section not found with id: " + sectionId + " in course: " + courseId,
                    HttpStatus.NOT_FOUND);
        }
        return section;
    }

    private MultimediaEntity getResourceOrThrow(Long sectionId, Long resourceId) {
        return multimediaRepository.findByIdAndSectionId(resourceId, sectionId)
                .orElseThrow(() -> new ApiException(
                        "Resource not found with id: " + resourceId + " in section: " + sectionId, HttpStatus.NOT_FOUND));
    }

    // ---------- storage (Cloudinary) ----------

    private StoredFile uploadToStorage(MultipartFile file, Long courseId, Long sectionId) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "lms/multimedia/course-" + courseId + "/section-" + sectionId,
                            "resource_type", "auto",   // image / video (incl. audio) / raw are detected automatically
                            "use_filename", true,
                            "unique_filename", true
                    )
            );
            Object secureUrl = result.get("secure_url");
            Object publicId = result.get("public_id");
            Object resourceType = result.get("resource_type");
            if (secureUrl == null || publicId == null || resourceType == null) {
                log.error("Cloudinary upload returned an incomplete response: {}", result);
                throw new ApiException("Unable to upload file. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return new StoredFile(secureUrl.toString(), publicId.toString(), resourceType.toString());
        } catch (IOException e) {
            log.error("Failed to upload multimedia to Cloudinary. originalFilename='{}'", file.getOriginalFilename(), e);
            throw new ApiException("Unable to upload file. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Best effort: a failed cleanup must never fail the user's request.
    private void deleteFromStorage(String publicId, String storageResourceType) {
        try {
            cloudinary.uploader().destroy(publicId,
                    ObjectUtils.asMap("resource_type", storageResourceType, "invalidate", true));
        } catch (Exception e) {
            log.warn("Could not delete Cloudinary asset '{}' ({})", publicId, storageResourceType, e);
        }
    }

    // ---------- mapping ----------

    private MultimediaDto.MultimediaResponse toResponse(MultimediaEntity m) {
        return MultimediaDto.MultimediaResponse.builder()
                .id(m.getId())
                .courseId(m.getSection().getCourse().getId())
                .sectionId(m.getSection().getId())
                .name(m.getName())
                .description(m.getDescription())
                .resourceType(m.getResourceType())
                .fileUrl(m.getFileUrl())
                .originalFileName(m.getOriginalFileName())
                .fileExtension(m.getFileExtension())
                .contentType(m.getContentType())
                .fileSize(m.getFileSize())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}