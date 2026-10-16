package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.QuestionDto;
import com.example.lms.service.QuestionService;
import com.example.lms.service.QuestionTransferService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/courses/{courseId}/sections/{sectionId}/questions")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")   // SRS: only Admin and Instructor manage Question Banks
public class QuestionController {

    private final QuestionService questionService;
    private final QuestionTransferService transferService;

    public QuestionController(QuestionService questionService, QuestionTransferService transferService) {
        this.questionService = questionService;
        this.transferService = transferService;
    }

    // ---------- CRUD ----------

    @PostMapping
    public ResponseEntity<ApiResponse<QuestionDto.QuestionResponse>> createQuestion(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @Valid @RequestBody QuestionDto.QuestionRequest request) {
        QuestionDto.QuestionResponse created = questionService.createQuestion(courseId, sectionId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Question created successfully.", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<QuestionDto.QuestionResponse>>> getQuestions(
            @PathVariable Long courseId,
            @PathVariable Long sectionId) {
        return ResponseEntity.ok(ApiResponse.success("Questions fetched successfully.",
                questionService.getQuestions(courseId, sectionId)));
    }

    @GetMapping("/{questionId}")
    public ResponseEntity<ApiResponse<QuestionDto.QuestionResponse>> getQuestion(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @PathVariable Long questionId) {
        return ResponseEntity.ok(ApiResponse.success("Question fetched successfully.",
                questionService.getQuestion(courseId, sectionId, questionId)));
    }

    @PutMapping("/{questionId}")
    public ResponseEntity<ApiResponse<QuestionDto.QuestionResponse>> updateQuestion(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @PathVariable Long questionId,
            @Valid @RequestBody QuestionDto.QuestionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Question updated successfully.",
                questionService.updateQuestion(courseId, sectionId, questionId, request)));
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @PathVariable Long questionId) {
        questionService.deleteQuestion(courseId, sectionId, questionId);
        return ResponseEntity.ok(ApiResponse.success("Question deleted successfully."));
    }

    // ---------- Duplicate / Copy / Cut(Move) / Hide ----------

    @PostMapping("/{questionId}/duplicate")
    public ResponseEntity<ApiResponse<QuestionDto.QuestionResponse>> duplicateQuestion(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @PathVariable Long questionId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Question duplicated successfully.",
                questionService.duplicateQuestion(courseId, sectionId, questionId)));
    }

    @PostMapping("/{questionId}/copy")
    public ResponseEntity<ApiResponse<QuestionDto.QuestionResponse>> copyQuestion(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @PathVariable Long questionId,
            @Valid @RequestBody QuestionDto.TargetSectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Question copied successfully.",
                questionService.copyQuestion(courseId, sectionId, questionId, request.getTargetSectionId())));
    }

    @PostMapping("/{questionId}/move")
    public ResponseEntity<ApiResponse<QuestionDto.QuestionResponse>> moveQuestion(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @PathVariable Long questionId,
            @Valid @RequestBody QuestionDto.TargetSectionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Question moved successfully.",
                questionService.moveQuestion(courseId, sectionId, questionId, request.getTargetSectionId())));
    }

    @PatchMapping("/{questionId}/visibility")
    public ResponseEntity<ApiResponse<QuestionDto.QuestionResponse>> setVisibility(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @PathVariable Long questionId,
            @Valid @RequestBody QuestionDto.VisibilityRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Question visibility updated successfully.",
                questionService.setHidden(courseId, sectionId, questionId, request.getHidden())));
    }

    @PatchMapping("/reorder")
    public ResponseEntity<ApiResponse<List<QuestionDto.QuestionResponse>>> reorderQuestions(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @Valid @RequestBody QuestionDto.ReorderRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Questions reordered successfully.",
                questionService.reorderQuestions(courseId, sectionId, request)));
    }

    // ---------- Existing Questions ----------

    @GetMapping("/existing")
    public ResponseEntity<ApiResponse<QuestionDto.ExistingQuestionsResponse>> searchExisting(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Existing questions fetched successfully.",
                questionService.searchExisting(courseId, sectionId, keyword, page, size)));
    }

    @PostMapping("/existing")
    public ResponseEntity<ApiResponse<List<QuestionDto.QuestionResponse>>> addExisting(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @Valid @RequestBody QuestionDto.AddExistingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Questions added successfully.",
                questionService.addExistingQuestions(courseId, sectionId, request)));
    }

    // ---------- Import / Export ----------

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<QuestionDto.ImportResponse>> importQuestions(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Questions imported successfully.",
                transferService.importQuestions(courseId, sectionId, file)));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportQuestions(
            @PathVariable Long courseId,
            @PathVariable Long sectionId) {
        byte[] body = transferService.exportQuestions(courseId, sectionId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("questions-section-" + sectionId + ".csv", StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}