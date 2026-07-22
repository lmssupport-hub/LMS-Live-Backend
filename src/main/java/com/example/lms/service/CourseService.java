package com.example.lms.service;

import com.example.lms.dto.CourseDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.repository.CourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CourseService {

    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Transactional
    public CourseDto.CourseResponse createCourse(CourseDto.CourseRequest request) {
        String name = validateName(request.getName());

        if (courseRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A course with the same name already exists.");
        }

        CourseEntity course = CourseEntity.builder()
                .name(name)
                .category(request.getCategory())
                .instructorId(request.getInstructorId())
                .courseLevel(request.getCourseLevel())
                .thumbnailUrl(request.getThumbnailUrl())
                .build();

        return toResponse(courseRepository.save(course));
    }

    @Transactional(readOnly = true)
    public List<CourseDto.CourseResponse> getAllCourses() {
        return courseRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseDto.CourseResponse getCourseById(Long courseId) {
        return toResponse(getCourseOrThrow(courseId));
    }

    @Transactional
    public CourseDto.CourseResponse updateCourse(Long courseId, CourseDto.CourseRequest request) {
        CourseEntity course = getCourseOrThrow(courseId);
        String name = validateName(request.getName());

        if (courseRepository.existsByNameIgnoreCaseAndIdNot(name, courseId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A course with the same name already exists.");
        }

        course.setName(name);
        course.setCategory(request.getCategory());
        course.setInstructorId(request.getInstructorId());
        course.setCourseLevel(request.getCourseLevel());
        course.setThumbnailUrl(request.getThumbnailUrl());

        return toResponse(courseRepository.save(course));
    }

    @Transactional
    public void deleteCourse(Long courseId) {
        CourseEntity course = getCourseOrThrow(courseId);
        courseRepository.delete(course);
    }

    // ---------- helpers ----------

    private CourseEntity getCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Course not found with id: " + courseId));
    }

    private String validateName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course Name is required.");
        }
        return name;
    }

    private CourseDto.CourseResponse toResponse(CourseEntity c) {
        return CourseDto.CourseResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .category(c.getCategory())
                .instructorId(c.getInstructorId())
                .courseLevel(c.getCourseLevel())
                .thumbnailUrl(c.getThumbnailUrl())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
