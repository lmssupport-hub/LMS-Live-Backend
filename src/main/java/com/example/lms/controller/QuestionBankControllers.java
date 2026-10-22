package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.QuestionDto;
import com.example.lms.entity.QuestionBankEntities.QuestionBankEntity;
import com.example.lms.entity.QuestionBankEntities.QuestionEntity;
import com.example.lms.entity.QuestionBankEntities.QuestionOptionEntity;
import com.example.lms.entity.QuestionBankEntities.QuestionSectionEntity;
import com.example.lms.repository.QuestionBankRepository;
import com.example.lms.repository.QuestionRepository;
import com.example.lms.repository.QuestionSectionRepository;
import com.example.lms.service.QuestionBankServices.QuestionBankService;
import com.example.lms.service.QuestionBankServices.QuestionMapper;
import com.example.lms.service.QuestionBankServices.QuestionSectionService;
import com.example.lms.service.QuestionBankServices.QuestionService;
import com.example.lms.service.QuestionBankServices.QuestionTransferService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** All Question Bank controller classes in one file (static nested classes). */
public class QuestionBankControllers {

    private QuestionBankControllers() { }

    /** Question Banks added as an activity inside an organize section. */
    @RestController
    @RequestMapping("/api/courses/{courseId}/sections/{sectionId}/question-banks")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public static class QuestionBankController {

        private final QuestionBankService bankService;

        public QuestionBankController(QuestionBankService bankService) {
            this.bankService = bankService;
        }

        @PostMapping
        public ResponseEntity<ApiResponse<QuestionDto.BankResponse>> create(
                @PathVariable Long courseId, @PathVariable Long sectionId,
                @Valid @RequestBody QuestionDto.BankRequest request) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                    "Question bank created successfully.", bankService.createBank(courseId, sectionId, request)));
        }

        @GetMapping
        public ResponseEntity<ApiResponse<List<QuestionDto.BankResponse>>> list(
                @PathVariable Long courseId, @PathVariable Long sectionId) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Question banks fetched successfully.", bankService.getBanks(courseId, sectionId)));
        }

        @GetMapping("/{bankId}")
        public ResponseEntity<ApiResponse<QuestionDto.BankResponse>> get(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Question bank fetched successfully.", bankService.getBank(courseId, sectionId, bankId)));
        }

        @PutMapping("/{bankId}")
        public ResponseEntity<ApiResponse<QuestionDto.BankResponse>> update(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @Valid @RequestBody QuestionDto.BankRequest request) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Question bank updated successfully.", bankService.updateBank(courseId, sectionId, bankId, request)));
        }

        @DeleteMapping("/{bankId}")
        public ResponseEntity<ApiResponse<Void>> delete(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId) {
            bankService.deleteBank(courseId, sectionId, bankId);
            return ResponseEntity.ok(ApiResponse.success("Question bank deleted successfully."));
        }
    }

    /** Question Sections (Section 1, Section 2 ...) inside one Question Bank. */
    @RestController
    @RequestMapping("/api/courses/{courseId}/sections/{sectionId}/question-banks/{bankId}/question-sections")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public static class QuestionSectionController {

        private final QuestionSectionService service;

        public QuestionSectionController(QuestionSectionService service) {
            this.service = service;
        }

        @PostMapping
        public ResponseEntity<ApiResponse<QuestionDto.QuestionSectionResponse>> create(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @Valid @RequestBody QuestionDto.QuestionSectionRequest request) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                    "Section created successfully.", service.create(courseId, sectionId, bankId, request)));
        }

        @GetMapping
        public ResponseEntity<ApiResponse<List<QuestionDto.QuestionSectionResponse>>> list(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Sections fetched successfully.", service.getAll(courseId, sectionId, bankId)));
        }

        @GetMapping("/{questionSectionId}")
        public ResponseEntity<ApiResponse<QuestionDto.QuestionSectionResponse>> get(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Section fetched successfully.", service.getOne(courseId, sectionId, bankId, questionSectionId)));
        }

        @PutMapping("/{questionSectionId}")
        public ResponseEntity<ApiResponse<QuestionDto.QuestionSectionResponse>> update(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId,
                @Valid @RequestBody QuestionDto.QuestionSectionRequest request) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Section updated successfully.", service.update(courseId, sectionId, bankId, questionSectionId, request)));
        }

        @DeleteMapping("/{questionSectionId}")
        public ResponseEntity<ApiResponse<Void>> delete(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId) {
            service.delete(courseId, sectionId, bankId, questionSectionId);
            return ResponseEntity.ok(ApiResponse.success("Section deleted successfully."));
        }
    }

    /**
     * Questions inside a QUESTION SECTION of a Question Bank:
     * Course -> Section -> Question Bank -> Question Section -> Questions
     */
    @RestController
    @RequestMapping("/api/courses/{courseId}/sections/{sectionId}/question-banks/{bankId}"
            + "/question-sections/{questionSectionId}/questions")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")   // SRS: only Admin and Instructor manage Question Banks
    public static class QuestionController {

        private final QuestionService questionService;
        private final QuestionTransferService transferService;

        public QuestionController(QuestionService questionService, QuestionTransferService transferService) {
            this.questionService = questionService;
            this.transferService = transferService;
        }

        // ---------- CRUD ----------

        @PostMapping
        public ResponseEntity<ApiResponse<QuestionDto.QuestionResponse>> createQuestion(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId,
                @Valid @RequestBody QuestionDto.QuestionRequest request) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Question created successfully.",
                            questionService.createQuestion(courseId, sectionId, bankId, questionSectionId, request)));
        }

        @GetMapping
        public ResponseEntity<ApiResponse<List<QuestionDto.QuestionResponse>>> getQuestions(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId) {
            return ResponseEntity.ok(ApiResponse.success("Questions fetched successfully.",
                    questionService.getQuestions(courseId, sectionId, bankId, questionSectionId)));
        }

        @GetMapping("/{questionId}")
        public ResponseEntity<ApiResponse<QuestionDto.QuestionResponse>> getQuestion(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId, @PathVariable Long questionId) {
            return ResponseEntity.ok(ApiResponse.success("Question fetched successfully.",
                    questionService.getQuestion(courseId, sectionId, bankId, questionSectionId, questionId)));
        }

        @PutMapping("/{questionId}")
        public ResponseEntity<ApiResponse<QuestionDto.QuestionResponse>> updateQuestion(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId, @PathVariable Long questionId,
                @Valid @RequestBody QuestionDto.QuestionRequest request) {
            return ResponseEntity.ok(ApiResponse.success("Question updated successfully.",
                    questionService.updateQuestion(courseId, sectionId, bankId, questionSectionId, questionId, request)));
        }

        @DeleteMapping("/{questionId}")
        public ResponseEntity<ApiResponse<Void>> deleteQuestion(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId, @PathVariable Long questionId) {
            questionService.deleteQuestion(courseId, sectionId, bankId, questionSectionId, questionId);
            return ResponseEntity.ok(ApiResponse.success("Question deleted successfully."));
        }

        // ---------- Duplicate / Copy / Cut(Move) / Hide ----------

        @PostMapping("/{questionId}/duplicate")
        public ResponseEntity<ApiResponse<QuestionDto.QuestionResponse>> duplicateQuestion(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId, @PathVariable Long questionId) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Question duplicated successfully.",
                    questionService.duplicateQuestion(courseId, sectionId, bankId, questionSectionId, questionId)));
        }

        @PostMapping("/{questionId}/copy")
        public ResponseEntity<ApiResponse<QuestionDto.QuestionResponse>> copyQuestion(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId, @PathVariable Long questionId,
                @Valid @RequestBody QuestionDto.TargetSectionRequest request) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Question copied successfully.",
                    questionService.copyQuestion(courseId, sectionId, bankId, questionSectionId, questionId,
                            request.getTargetQuestionSectionId())));
        }

        @PostMapping("/{questionId}/move")
        public ResponseEntity<ApiResponse<QuestionDto.QuestionResponse>> moveQuestion(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId, @PathVariable Long questionId,
                @Valid @RequestBody QuestionDto.TargetSectionRequest request) {
            return ResponseEntity.ok(ApiResponse.success("Question moved successfully.",
                    questionService.moveQuestion(courseId, sectionId, bankId, questionSectionId, questionId,
                            request.getTargetQuestionSectionId())));
        }

        @PatchMapping("/{questionId}/visibility")
        public ResponseEntity<ApiResponse<QuestionDto.QuestionResponse>> setVisibility(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId, @PathVariable Long questionId,
                @Valid @RequestBody QuestionDto.VisibilityRequest request) {
            return ResponseEntity.ok(ApiResponse.success("Question visibility updated successfully.",
                    questionService.setHidden(courseId, sectionId, bankId, questionSectionId, questionId, request.getHidden())));
        }

        @PatchMapping("/reorder")
        public ResponseEntity<ApiResponse<List<QuestionDto.QuestionResponse>>> reorderQuestions(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId,
                @Valid @RequestBody QuestionDto.ReorderRequest request) {
            return ResponseEntity.ok(ApiResponse.success("Questions reordered successfully.",
                    questionService.reorderQuestions(courseId, sectionId, bankId, questionSectionId, request)));
        }

        // ---------- Existing Questions (same Question Bank, other sections) ----------

        @GetMapping("/existing")
        public ResponseEntity<ApiResponse<QuestionDto.ExistingQuestionsResponse>> searchExisting(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId,
                @RequestParam(required = false) String keyword,
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "20") int size) {
            return ResponseEntity.ok(ApiResponse.success("Existing questions fetched successfully.",
                    questionService.searchExisting(courseId, sectionId, bankId, questionSectionId, keyword, page, size)));
        }

        @PostMapping("/existing")
        public ResponseEntity<ApiResponse<List<QuestionDto.QuestionResponse>>> addExisting(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId,
                @Valid @RequestBody QuestionDto.AddExistingRequest request) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Questions added successfully.",
                    questionService.addExistingQuestions(courseId, sectionId, bankId, questionSectionId, request)));
        }

        // ---------- Import / Export ----------

        @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ApiResponse<QuestionDto.ImportResponse>> importQuestions(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId,
                @RequestPart("file") MultipartFile file) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Questions imported successfully.",
                    transferService.importQuestions(courseId, sectionId, bankId, questionSectionId, file)));
        }

        @GetMapping("/export")
        public ResponseEntity<byte[]> exportQuestions(
                @PathVariable Long courseId, @PathVariable Long sectionId, @PathVariable Long bankId,
                @PathVariable Long questionSectionId) {
            byte[] body = transferService.exportQuestions(courseId, sectionId, bankId, questionSectionId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("questions-section-" + questionSectionId + ".csv", StandardCharsets.UTF_8).build().toString())
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .body(body);
        }
    }
}
