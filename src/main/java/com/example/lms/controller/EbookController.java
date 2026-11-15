package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.EbookDto;
import com.example.lms.service.EbookService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ebooks of a course section. Managing needs the Ebook permission (CONTENT_MGMT_EBOOK);
 * reading (list / get) is open to anyone who may read the course (checked in EbookService),
 * so learners see the ebook in the course content viewer.
 */
@RestController
@RequestMapping("/api/courses/{courseId}/sections/{sectionId}/ebooks")
@SecurityRequirement(name = "bearerAuth")
public class EbookController {

    private final EbookService ebookService;

    public EbookController(EbookService ebookService) {
        this.ebookService = ebookService;
    }

    @PostMapping
    @PreAuthorize("@perm.can(authentication, 'CONTENT_MGMT_EBOOK', 'create')")
    public ResponseEntity<ApiResponse<EbookDto.EbookResponse>> create(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @RequestBody EbookDto.EbookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ebook saved successfully.", ebookService.create(courseId, sectionId, request)));
    }

    /** Names only (no content). */
    @GetMapping
    public ResponseEntity<ApiResponse<List<EbookDto.EbookSummaryResponse>>> list(
            @PathVariable Long courseId,
            @PathVariable Long sectionId) {
        return ResponseEntity.ok(ApiResponse.success("Ebooks fetched successfully.", ebookService.list(courseId, sectionId)));
    }

    @GetMapping("/{ebookId}")
    public ResponseEntity<ApiResponse<EbookDto.EbookResponse>> get(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @PathVariable Long ebookId) {
        return ResponseEntity.ok(ApiResponse.success("Ebook fetched successfully.", ebookService.get(courseId, sectionId, ebookId)));
    }

    @PutMapping("/{ebookId}")
    @PreAuthorize("@perm.can(authentication, 'CONTENT_MGMT_EBOOK', 'update')")
    public ResponseEntity<ApiResponse<EbookDto.EbookResponse>> update(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @PathVariable Long ebookId,
            @RequestBody EbookDto.EbookRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Ebook saved successfully.",
                ebookService.update(courseId, sectionId, ebookId, request)));
    }

    @DeleteMapping("/{ebookId}")
    @PreAuthorize("@perm.can(authentication, 'CONTENT_MGMT_EBOOK', 'delete')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @PathVariable Long ebookId) {
        ebookService.delete(courseId, sectionId, ebookId);
        return ResponseEntity.ok(ApiResponse.<Void>success("Ebook deleted successfully."));
    }
}