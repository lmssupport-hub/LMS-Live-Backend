package com.example.lms.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.lms.dto.CourseDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.EnrollmentEntity;
import com.example.lms.entity.EnrollmentStatus;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.EnrollmentRepository;
import com.example.lms.repository.SectionRepository;
import com.example.lms.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);

    private static final Map<Integer, String> CATEGORIES = Map.of(
            1, "Technical",
            2, "Soft Skills",
            3, "Compliance",
            4, "Leadership"
    );

    private static final Set<String> ALLOWED_LEVELS = Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");
    private static final Set<String> ALLOWED_STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> ALLOWED_THUMBNAIL_TYPES = Set.of("image/jpeg", "image/png");
    private static final long MAX_THUMBNAIL_BYTES = 5L * 1024 * 1024;

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final SectionRepository sectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final Cloudinary cloudinary;
    private final CourseAccessService courseAccess;

    public CourseService(CourseRepository courseRepository,
                         UserRepository userRepository,
                         SectionRepository sectionRepository,
                         EnrollmentRepository enrollmentRepository,
                         Cloudinary cloudinary,
                         CourseAccessService courseAccess) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.sectionRepository = sectionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.cloudinary = cloudinary;
        this.courseAccess = courseAccess;
    }

    @Transactional
    public CourseDto.CourseResponse createCourse(CourseDto.CourseRequest request, MultipartFile thumbnail) {
        String name = validateName(request.getName());
        Integer categoryId = validateCategory(request.getCategoryId());
        String level = validateLevel(request.getLevel());
        String status = validateStatus(request.getStatus());
        // CHANGED: the course belongs to the creator's organization; instructor + name are checked inside it.
        Long organizationId = courseAccess.currentOrganizationId();
        Long instructorId = validateInstructor(request.getInstructorId(), organizationId);

        if (courseRepository.existsByNameIgnoreCaseAndOrganizationId(name, organizationId)) {
            throw new ApiException("A course with the same name already exists.", HttpStatus.CONFLICT);
        }

        String thumbnailUrl = storeThumbnail(thumbnail);

        CourseEntity course = CourseEntity.builder()
                .organizationId(organizationId)
                .name(name)
                .description(request.getDescription())
                .categoryId(categoryId)
                .instructorId(instructorId)
                .level(level)
                .status(status)
                .thumbnailUrl(thumbnailUrl)
                .build();

        return toResponse(courseRepository.save(course), null);
    }

    /** CHANGED: only the courses of ONE organization (was findAll() = every organization). */
    private List<CourseDto.CourseResponse> getOrganizationCourses(Long organizationId) {
        return courseRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream().map(c -> toResponse(c, null)).toList();
    }

    /**
     * What the Course List shows for this user. Always limited to the user's own organization.
     *  - a role that can create, edit, delete or organize courses (and the company admin) -> every course of the organization
     *  - everyone else with "read" on Course List (and every LEARNER) -> every PUBLISHED course of the organization,
     *    each flagged enrolled = true ("Start course") or false ("Enroll")
     *  - no "read" on Course List -> 403
     */
    @Transactional(readOnly = true)
    public List<CourseDto.CourseResponse> getVisibleCourses(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found.", HttpStatus.UNAUTHORIZED));
        if (!courseAccess.canViewCourseList(user)) {
            throw new ApiException("You do not have permission to view courses.", HttpStatus.FORBIDDEN);
        }
        Long organizationId = PackageService.resolveOwnerId(user);
        if (courseAccess.isManager(user)) {
            return getOrganizationCourses(organizationId);
        }
        return getCoursesForLearner(user, organizationId);
    }

    /**
     * CHANGED: a learner sees ALL published courses of his own organization. A course he is enrolled in
     * is marked enrolled = true; the others are enrolled = false so the card shows "Enroll".
     */
    private List<CourseDto.CourseResponse> getCoursesForLearner(User user, Long organizationId) {
        Set<Long> enrolledIds = new HashSet<>(enrollmentRepository.findCourseIdsByUserId(user.getId()));
        return courseRepository
                .findByOrganizationIdAndStatusIgnoreCaseOrderByCreatedAtDesc(organizationId, "PUBLISHED")
                .stream()
                .map(c -> toResponse(c, enrolledIds.contains(c.getId())))
                .toList();
    }

    /**
     * NEW: learner self-enrollment. Only a PUBLISHED course of the caller's own organization can be joined;
     * enrolling twice is a no-op.
     */
    @Transactional
    public CourseDto.CourseResponse selfEnroll(Long courseId) {
        User user = courseAccess.currentUser();
        if (!courseAccess.canViewCourseList(user)) {
            throw new ApiException("You do not have permission to enroll in courses.", HttpStatus.FORBIDDEN);
        }
        CourseEntity course = courseAccess.requireCourseInOrganization(courseId);
        if (!"PUBLISHED".equalsIgnoreCase(course.getStatus())) {
            throw new ApiException("Course not found with id: " + courseId, HttpStatus.NOT_FOUND);
        }
        if (!enrollmentRepository.existsByCourseIdAndUserId(courseId, user.getId())) {
            try {
                enrollmentRepository.saveAndFlush(EnrollmentEntity.builder()
                        .courseId(courseId)
                        .userId(user.getId())
                        .enrolledAt(LocalDateTime.now())
                        .progressPercent(0)
                        .status(EnrollmentStatus.NOT_STARTED)
                        .build());
            } catch (DataIntegrityViolationException e) {
                throw new ApiException("You are already enrolled in this course. Please refresh.", HttpStatus.CONFLICT);
            }
        }
        return toResponse(course, true);
    }

    @Transactional(readOnly = true)
    public CourseDto.CourseResponse getCourseById(Long courseId) {
        // CHANGED: own organization only; a learner must be enrolled in a published course.
        return toResponse(courseAccess.requireReadableCourse(courseId), null);
    }

    @Transactional
    public CourseDto.CourseResponse updateCourse(Long courseId, CourseDto.CourseRequest request, MultipartFile thumbnail) {
        CourseEntity course = getCourseOrThrow(courseId);

        String name = validateName(request.getName());
        Integer categoryId = validateCategory(request.getCategoryId());
        String level = validateLevel(request.getLevel());
        String status = validateStatus(request.getStatus());
        Long instructorId = validateInstructor(request.getInstructorId(), course.getOrganizationId());

        if (courseRepository.existsByNameIgnoreCaseAndOrganizationIdAndIdNot(name, course.getOrganizationId(), courseId)) {
            throw new ApiException("A course with the same name already exists.", HttpStatus.CONFLICT);
        }

        course.setName(name);
        course.setDescription(request.getDescription());
        course.setCategoryId(categoryId);
        course.setInstructorId(instructorId);
        course.setLevel(level);
        course.setStatus(status);
        if (thumbnail != null && !thumbnail.isEmpty()) {
            course.setThumbnailUrl(storeThumbnail(thumbnail));
        }

        return toResponse(courseRepository.save(course), null);
    }

    /**
     * CHANGED: deleting a course now removes its dependent rows first (enrollments, then sections)
     * and finally the course itself, all inside ONE transaction. Previously only the course row was
     * deleted, so the foreign key from sections (fk_section_course) rejected it and the user saw
     * "This request conflicts with an existing record."
     *
     * If anything fails, the whole transaction rolls back, so nothing is half-deleted.
     */
    @Transactional
    public void deleteCourse(Long courseId) {
        getCourseOrThrow(courseId); // 404 if the course does not exist

        // children first, parent last
        int removedEnrollments = enrollmentRepository.deleteByCourseId(courseId);
        int removedSections = sectionRepository.deleteByCourseId(courseId);
        courseRepository.deleteById(courseId);

        try {
            // flush here so any remaining FK problem surfaces with a clear message
            courseRepository.flush();
        } catch (DataIntegrityViolationException e) {
            log.warn("Course delete blocked by remaining references. courseId={}", courseId, e);
            throw new ApiException(
                    "This course cannot be deleted because other records still depend on it.",
                    HttpStatus.CONFLICT);
        }

        log.info("Course deleted. courseId={}, enrollmentsRemoved={}, sectionsRemoved={}",
                courseId, removedEnrollments, removedSections);
    }

    // ---------- helpers ----------

    private CourseEntity getCourseOrThrow(Long courseId) {
        // CHANGED: only a course of the caller's own organization is found (404 otherwise).
        return courseAccess.requireCourseInOrganization(courseId);
    }

    private String validateName(String rawName) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) throw new ApiException("Course Name is required.", HttpStatus.BAD_REQUEST);
        if (name.length() < 3 || name.length() > 100) {
            throw new ApiException("Course Name must be between 3 and 100 characters.", HttpStatus.BAD_REQUEST);
        }
        return name;
    }

    private Integer validateCategory(Integer categoryId) {
        if (categoryId == null || !CATEGORIES.containsKey(categoryId)) {
            throw new ApiException("Course Category is invalid.", HttpStatus.BAD_REQUEST);
        }
        return categoryId;
    }

    private String validateLevel(String rawLevel) {
        String level = rawLevel == null ? "" : rawLevel.trim().toUpperCase();
        if (!ALLOWED_LEVELS.contains(level)) {
            throw new ApiException("Course Level must be one of: " + ALLOWED_LEVELS, HttpStatus.BAD_REQUEST);
        }
        return level;
    }

    private String validateStatus(String rawStatus) {
        String status = rawStatus == null ? "" : rawStatus.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new ApiException("Course Status must be one of: " + ALLOWED_STATUSES, HttpStatus.BAD_REQUEST);
        }
        return status;
    }

    /**
     * instructorId is validated server-side: it must belong to an active INSTRUCTOR
     * OF THE SAME ORGANIZATION, the same way category/level/status are validated.
     */
    private Long validateInstructor(Long instructorId, Long organizationId) {
        if (instructorId == null) {
            throw new ApiException("Instructor selection is required.", HttpStatus.BAD_REQUEST);
        }
        boolean isActiveInstructor = userRepository.findById(instructorId)
                .filter(u -> "INSTRUCTOR".equalsIgnoreCase(u.getRole()))
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .filter(u -> organizationId != null && organizationId.equals(PackageService.resolveOwnerId(u)))
                .isPresent();
        if (!isActiveInstructor) {
            throw new ApiException("Selected instructor is invalid or inactive.", HttpStatus.BAD_REQUEST);
        }
        return instructorId;
    }

    /**
     * Uploads to Cloudinary (permanent, CDN-backed store outside the app's own container)
     * and returns the public HTTPS URL to save in the DB.
     */
    private String storeThumbnail(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        if (!ALLOWED_THUMBNAIL_TYPES.contains(file.getContentType())) {
            throw new ApiException("Invalid file format.", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_THUMBNAIL_BYTES) {
            throw new ApiException("File size exceeds 5 MB", HttpStatus.BAD_REQUEST);
        }
        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "lms/thumbnails",
                            "resource_type", "image"
                    )
            );
            Object secureUrl = uploadResult.get("secure_url");
            if (secureUrl == null) {
                log.error("Cloudinary upload returned no secure_url. Full response: {}", uploadResult);
                throw new ApiException("Unable to store thumbnail. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return secureUrl.toString();
        } catch (IOException e) {
            log.error("Failed to upload thumbnail to Cloudinary. originalFilename='{}'",
                    file.getOriginalFilename(), e);
            throw new ApiException("Unable to store thumbnail. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private CourseDto.CourseResponse toResponse(CourseEntity c, Boolean enrolled) {
        return CourseDto.CourseResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .categoryId(c.getCategoryId())
                .categoryName(CATEGORIES.getOrDefault(c.getCategoryId(), "Uncategorized"))
                .instructorId(c.getInstructorId())
                .instructorName(resolveInstructorName(c.getInstructorId()))
                .level(c.getLevel())
                .status(c.getStatus())
                .thumbnailUrl(c.getThumbnailUrl())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .enrolled(enrolled)
                .build();
    }

    /** Resolves the display name for an instructor via the real user table. */
    private String resolveInstructorName(Long instructorId) {
        if (instructorId == null) return "Unknown Instructor";
        return userRepository.findById(instructorId)
                .map(u -> (u.getFirstName() + " " + u.getLastName()).trim())
                .filter(name -> !name.isEmpty())
                .orElse("Unknown Instructor");
    }
}