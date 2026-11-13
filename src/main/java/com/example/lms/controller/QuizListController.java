package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.QuestionDto;
import com.example.lms.service.QuizAttemptService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Quizzes (Question Banks) of a course section, for the learner's course content page.
 * The instructor list (/question-banks) needs the "organize modules" permission, so it can't be used by learners.
 * Access (enrolled learner / manager of the organization) is checked in QuizAttemptService.
 */
@RestController
@RequestMapping("/api/courses/{courseId}/sections/{sectionId}/quizzes")
@SecurityRequirement(name = "bearerAuth")
public class QuizListController {

    private final QuizAttemptService quizService;

    public QuizListController(QuizAttemptService quizService) {
        this.quizService = quizService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<QuestionDto.BankResponse>>> list(
            @PathVariable Long courseId, @PathVariable Long sectionId) {
        return ResponseEntity.ok(ApiResponse.success("Quizzes fetched successfully.",
                quizService.listQuizzes(courseId, sectionId)));
    }
}