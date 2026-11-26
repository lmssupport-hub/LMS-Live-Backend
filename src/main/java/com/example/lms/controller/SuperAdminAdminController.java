package com.example.lms.controller;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.ProfileDto;
import com.example.lms.dto.SuperAdminAdminDto;
import com.example.lms.dto.UserManagementDto;
import com.example.lms.service.SuperAdminAdminBulkService;
import com.example.lms.service.SuperAdminAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Super Admin > Admins page (Angular route /super-admin-dashboard/admins). SUPER_ADMIN only. */
@RestController
@RequestMapping("/api/super-admin/admins")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminAdminController {

    private final SuperAdminAdminService adminService;
    private final SuperAdminAdminBulkService bulkService;

    /* ---- Bulk Admin's: Import / Export as CSV ---- */

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> importTemplate() {
        return file(bulkService.buildTemplate(), "admins-import-template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SuperAdminAdminDto.ImportResult>> importAdmins(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestPart("file") MultipartFile file) {
        SuperAdminAdminDto.ImportResult result = bulkService.importAdmins(principal.getId(), file);
        return ResponseEntity.ok(ApiResponse.success(
                result.getImported() + " of " + result.getTotal() + " admins imported.", result));
    }

    /** POST so that a large row selection does not have to fit in the URL. */
    @PostMapping("/export")
    public ResponseEntity<byte[]> exportCsv(@RequestBody SuperAdminAdminDto.ExportRequest request) {
        return file(bulkService.exportCsv(request.getIds()), "admins.csv", "text/csv;charset=UTF-8");
    }

    private static ResponseEntity<byte[]> file(byte[] body, String name, String type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType(type))
                .body(body);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SuperAdminAdminDto.PageResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                "Admins fetched successfully.", adminService.list(page, size)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SuperAdminAdminDto.Row>> updateStatus(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UserManagementDto.StatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Admin status updated successfully.", adminService.updateStatus(principal.getId(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        adminService.delete(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Admin deleted successfully."));
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<ApiResponse<ProfileDto.Response>> getProfile(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile fetched successfully.", adminService.getProfile(id)));
    }

    @PutMapping("/{id}/profile")
    public ResponseEntity<ApiResponse<ProfileDto.Response>> updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody ProfileDto.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile updated successfully.", adminService.updateProfile(id, request)));
    }

    @PostMapping(value = "/{id}/profile/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileDto.Response>> uploadProfilePicture(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile picture updated successfully.", adminService.uploadProfilePicture(id, file)));
    }

    @PostMapping(value = "/{id}/profile/background-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileDto.Response>> uploadBackgroundPicture(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                "Background picture updated successfully.", adminService.uploadBackgroundPicture(id, file)));
    }

    @DeleteMapping("/{id}/profile/picture")
    public ResponseEntity<ApiResponse<ProfileDto.Response>> removeProfilePicture(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile picture removed.", adminService.removeProfilePicture(id)));
    }

    @DeleteMapping("/{id}/profile/background-picture")
    public ResponseEntity<ApiResponse<ProfileDto.Response>> removeBackgroundPicture(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Background picture removed.", adminService.removeBackgroundPicture(id)));
    }
}