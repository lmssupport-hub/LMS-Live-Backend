package com.example.lms.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.lms.dto.CourseDto;
import com.example.lms.entity.CourseEntity;
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

    public CourseService(CourseRepository courseRepository,
                         UserRepository userRepository,
                         SectionRepository sectionRepository,
                         EnrollmentRepository enrollmentRepository,
                         Cloudinary cloudinary) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.sectionRepository = sectionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.cloudinary = cloudinary;
    }

    @Transactional
    public CourseDto.CourseResponse createCourse(CourseDto.CourseRequest request, MultipartFile thumbnail) {
        String name = validateName(request.getName());
        Integer categoryId = validateCategory(request.getCategoryId());
        String level = validateLevel(request.getLevel());
        String status = validateStatus(request.getStatus());
        Long instructorId = validateInstructor(request.getInstructorId());

        if (courseRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException("A course with the same name already exists.", HttpStatus.CONFLICT);
        }

        String thumbnailUrl = storeThumbnail(thumbnail);

        CourseEntity course = CourseEntity.builder()
                .name(name)
                .description(request.getDescription())
                .categoryId(categoryId)
                .instructorId(instructorId)
                .level(level)
                .status(status)
                .thumbnailUrl(thumbnailUrl)
                .build();

        return toResponse(courseRepository.save(course));
    }

    @Transactional(readOnly = true)
    public List<CourseDto.CourseResponse> getAllCourses() {
        return courseRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * NEW: a learner only sees the PUBLISHED courses they are enrolled in.
     * {@code username} is the authenticated principal name (the user's email).
     */
    @Transactional(readOnly = true)
    public List<CourseDto.CourseResponse> getCoursesForLearner(String username) {
        Long userId = userRepository.findByEmailIgnoreCase(username)
                .map(u -> u.getId())
                .orElseThrow(() -> new ApiException("User not found.", HttpStatus.NOT_FOUND));

        List<Long> courseIds = enrollmentRepository.findCourseIdsByUserId(userId);
        if (courseIds.isEmpty()) {
            return List.of();
        }
        return courseRepository.findAllById(courseIds).stream()
                .filter(c -> "PUBLISHED".equalsIgnoreCase(c.getStatus()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseDto.CourseResponse getCourseById(Long courseId) {
        return toResponse(getCourseOrThrow(courseId));
    }

    @Transactional
    public CourseDto.CourseResponse updateCourse(Long courseId, CourseDto.CourseRequest request, MultipartFile thumbnail) {
        CourseEntity course = getCourseOrThrow(courseId);

        String name = validateName(request.getName());
        Integer categoryId = validateCategory(request.getCategoryId());
        String level = validateLevel(request.getLevel());
        String status = validateStatus(request.getStatus());
        Long instructorId = validateInstructor(request.getInstructorId());

        if (courseRepository.existsByNameIgnoreCaseAndIdNot(name, courseId)) {
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

        return toResponse(courseRepository.save(course));
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
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException("Course not found with id: " + courseId, HttpStatus.NOT_FOUND));
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
     * instructorId is validated server-side: it must belong to an active INSTRUCTOR,
     * the same way category/level/status are validated.
     */
    private Long validateInstructor(Long instructorId) {
        if (instructorId == null) {
            throw new ApiException("Instructor selection is required.", HttpStatus.BAD_REQUEST);
        }
        boolean isActiveInstructor = userRepository.findById(instructorId)
                .filter(u -> "INSTRUCTOR".equalsIgnoreCase(u.getRole()))
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
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

    private CourseDto.CourseResponse toResponse(CourseEntity c) {
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