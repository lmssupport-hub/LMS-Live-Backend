package com.example.lms.service;

import com.example.lms.dto.CourseDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public CourseService(CourseRepository courseRepository, UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
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

    @Transactional
    public void deleteCourse(Long courseId) {
        courseRepository.delete(getCourseOrThrow(courseId));
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
     * NEW - previously instructorId was taken from the request with no server-side check
     * that the id actually belongs to an active INSTRUCTOR, so a stale/bad id from the
     * client would silently save. Now it's validated the same way category/level/status are.
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

    private String storeThumbnail(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        if (!ALLOWED_THUMBNAIL_TYPES.contains(file.getContentType())) {
            throw new ApiException("Invalid file format.", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_THUMBNAIL_BYTES) {
            throw new ApiException("File size exceeds 5 MB", HttpStatus.BAD_REQUEST);
        }
        try {
            Path dir = Paths.get(uploadDir, "thumbnails");
            Files.createDirectories(dir);
            String ext = file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")
                    ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.'))
                    : "";
            String filename = UUID.randomUUID() + ext;
            Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/thumbnails/" + filename;
        } catch (IOException e) {
            // NEW: log the real underlying cause. Previously this exception was
            // silently swallowed - ApiException's handler in GlobalExceptionHandler
            // does not log, so nothing appeared in the console even though the
            // request failed with 500. This line is what actually reveals *why*
            // the write failed (permission denied, missing/read-only path, etc.).
            log.error("Failed to store thumbnail. uploadDir='{}', originalFilename='{}'",
                    uploadDir, file.getOriginalFilename(), e);
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