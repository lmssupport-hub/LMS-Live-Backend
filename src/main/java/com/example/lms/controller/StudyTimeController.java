package com.example.lms.controller;

import com.example.lms.service.StudyTimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Called by the learner's course content page every ~30 s while it is open and visible. */
@RestController
@RequestMapping("/api/courses/{courseId}/study-time")
@RequiredArgsConstructor
public class StudyTimeController {

    private final StudyTimeService studyTimeService;

    @PostMapping("/ping")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> ping(@PathVariable Long courseId) {
        studyTimeService.ping(courseId);
        return ResponseEntity.noContent().build();
    }
}