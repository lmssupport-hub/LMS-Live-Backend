package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.MultimediaDto;
import com.example.lms.service.MultimediaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/courses/{courseId}/sections/{sectionId}/multimedia")
public class MultimediaController {

    private final MultimediaService multimediaService;

    public MultimediaController(MultimediaService multimediaService) {
        this.multimediaService = multimediaService;
    }

    // "resource" = JSON part (name, description), "file" = the upload.
    // Both are required=false so the service returns the SRS messages
    // ("Resource Name is required." / "File is required.") instead of a generic 500.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MultimediaDto.MultimediaResponse>> uploadResource(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @RequestPart(value = "resource", required = false) MultimediaDto.MultimediaRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        MultimediaDto.MultimediaResponse created =
                multimediaService.createResource(courseId, sectionId, request, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Resource uploaded successfully.", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MultimediaDto.MultimediaResponse>>> getAllResources(
            @PathVariable Long courseId,
            @PathVariable Long sectionId) {
        return ResponseEntity.ok(ApiResponse.success("Resources fetched successfully.",
                multimediaService.getAllResources(courseId, sectionId)));
    }

    @GetMapping("/{resourceId}")
    public ResponseEntity<ApiResponse<MultimediaDto.MultimediaResponse>> getResourceById(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @PathVariable Long resourceId) {
        return ResponseEntity.ok(ApiResponse.success("Resource fetched successfully.",
                multimediaService.getResourceById(courseId, sectionId, resourceId)));
    }

    @PutMapping(value = "/{resourceId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MultimediaDto.MultimediaResponse>> updateResource(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @PathVariable Long resourceId,
            @RequestPart(value = "resource", required = false) MultimediaDto.MultimediaRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Resource updated successfully.",
                multimediaService.updateResource(courseId, sectionId, resourceId, request, file)));
    }

    @DeleteMapping("/{resourceId}")
    public ResponseEntity<ApiResponse<Void>> deleteResource(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @PathVariable Long resourceId) {
        multimediaService.deleteResource(courseId, sectionId, resourceId);
        return ResponseEntity.ok(ApiResponse.success("Resource deleted successfully."));
    }
}