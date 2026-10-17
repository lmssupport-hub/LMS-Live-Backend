package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.example.lms.dto.CourseDto;
import com.example.lms.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@SecurityRequirement(name = "bearerAuth")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseDto.CourseResponse>> createCourse(
            @RequestPart("course") @Valid CourseDto.CourseRequest request,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail) {
        CourseDto.CourseResponse created = courseService.createCourse(request, thumbnail);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course created successfully.", created));
    }

    // Admins/instructors get every course; a learner gets only the courses they are enrolled in.
    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseDto.CourseResponse>>> getAllCourses(Authentication authentication) {
        boolean learner = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_LEARNER".equals(a.getAuthority()));
        List<CourseDto.CourseResponse> courses = learner
                ? courseService.getCoursesForLearner(authentication.getName())
                : courseService.getAllCourses();
        return ResponseEntity.ok(ApiResponse.success("Courses fetched successfully.", courses));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseDto.CourseResponse>> getCourseById(@PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.success("Course fetched successfully.", courseService.getCourseById(courseId)));
    }

    @PutMapping(value = "/{courseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseDto.CourseResponse>> updateCourse(
            @PathVariable Long courseId,
            @RequestPart("course") @Valid CourseDto.CourseRequest request,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail) {
        CourseDto.CourseResponse updated = courseService.updateCourse(courseId, request, thumbnail);
        return ResponseEntity.ok(ApiResponse.success("Course updated successfully.", updated));
    }

    @DeleteMapping("/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(@PathVariable Long courseId) {
        courseService.deleteCourse(courseId);
        return ResponseEntity.ok(ApiResponse.success("Course deleted successfully."));
    }
}