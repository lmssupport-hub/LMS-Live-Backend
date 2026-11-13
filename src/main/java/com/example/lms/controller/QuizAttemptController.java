package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.QuizAttemptDto;
import com.example.lms.service.QuizAttemptService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Learner "Attempt Quiz". The existing question-bank controllers are for instructors / admins (organize modules);
 * these endpoints are for learners and never return the correct answers. Access rules (enrolled learner,
 * published course, bank belongs to the section) are checked in QuizAttemptService.
 */
@RestController
@RequestMapping("/api/courses/{courseId}/sections/{sectionId}/question-banks/{bankId}/attempt")
@SecurityRequirement(name = "bearerAuth")
public class QuizAttemptController {

    private final QuizAttemptService attemptService;

    public QuizAttemptController(QuizAttemptService attemptService) {
        this.attemptService = attemptService;
    }

    /** Opens the quiz page without starting it: intro (NOT_STARTED), resume (IN_PROGRESS) or the Score page (SUBMITTED). */
    @GetMapping
    public ResponseEntity<ApiResponse<QuizAttemptDto.AttemptResponse>> overview(
            @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId) {
        return ResponseEntity.ok(ApiResponse.success("Quiz loaded successfully.",
                attemptService.overview(courseId, sectionId, bankId)));
    }

    /** "Start Quiz": the timer starts now. */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<QuizAttemptDto.AttemptResponse>> start(
            @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId) {
        return ResponseEntity.ok(ApiResponse.success("Quiz started.",
                attemptService.start(courseId, sectionId, bankId)));
    }

    /** "Retest": a new attempt (while attempts are left). */
    @PostMapping("/retest")
    public ResponseEntity<ApiResponse<QuizAttemptDto.AttemptResponse>> retest(
            @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId) {
        return ResponseEntity.ok(ApiResponse.success("New attempt started.",
                attemptService.retest(courseId, sectionId, bankId)));
    }

    /** Correct / Incorrect Answer - View (only after finishing). */
    @GetMapping("/review")
    public ResponseEntity<ApiResponse<QuizAttemptDto.ReviewResponse>> review(
            @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId) {
        return ResponseEntity.ok(ApiResponse.success("Review fetched successfully.",
                attemptService.review(courseId, sectionId, bankId)));
    }

    /** Saves (or clears) the answer of one question. */
    @PutMapping("/answers/{questionId}")
    public ResponseEntity<ApiResponse<Void>> saveAnswer(
            @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
            @PathVariable Long questionId,
            @Valid @RequestBody QuizAttemptDto.SaveAnswerRequest request) {
        attemptService.saveAnswer(courseId, sectionId, bankId, questionId, request);
        return ResponseEntity.ok(ApiResponse.<Void>success("Answer saved."));
    }

    /** "Submit Section". */
    @PostMapping("/sections/{questionSectionId}/submit")
    public ResponseEntity<ApiResponse<QuizAttemptDto.AttemptResponse>> submitSection(
            @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
            @PathVariable Long questionSectionId) {
        return ResponseEntity.ok(ApiResponse.success("Section submitted.",
                attemptService.submitSection(courseId, sectionId, bankId, questionSectionId)));
    }

    /** "Finish": calculates the score. */
    @PostMapping("/finish")
    public ResponseEntity<ApiResponse<QuizAttemptDto.AttemptResponse>> finish(
            @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId) {
        return ResponseEntity.ok(ApiResponse.success("Quiz submitted.",
                attemptService.finish(courseId, sectionId, bankId)));
    }
}