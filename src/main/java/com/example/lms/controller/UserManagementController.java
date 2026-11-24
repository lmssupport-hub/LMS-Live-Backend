package com.example.lms.controller;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.ProfileDto;
import com.example.lms.dto.UserImportDto;
import com.example.lms.dto.UserManagementDto;
import com.example.lms.service.UserImportService;
import com.example.lms.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/**
 * "All Users" page (Angular route /admin-dashboard/users). Access comes from the package / role
 * permission of feature USER_MGMT_ALL_USERS (like the Course module):
 *   read   -> list users, open a user's profile
 *   update -> edit profile / pictures, change status (Active / Inactive / Suspended)
 *   delete -> delete users
 * The service scopes every user to the caller's company, whoever the caller is.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserManagementController {

    private final UserManagementService userManagementService;
    private final UserImportService userImportService;

    @PreAuthorize("@perm.can(authentication, 'USER_MGMT_ALL_USERS', 'read')")
    @GetMapping
    public ResponseEntity<ApiResponse<UserManagementDto.PageResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                "Users fetched successfully.",
                userManagementService.list(principal.getId(), search, status, page, size)));
    }

    @PreAuthorize("@perm.can(authentication, 'USER_MGMT_ALL_USERS', 'update')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserManagementDto.Row>> updateStatus(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UserManagementDto.StatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "User status updated successfully.",
                userManagementService.updateStatus(principal.getId(), id, request)));
    }

    @PreAuthorize("@perm.can(authentication, 'USER_MGMT_ALL_USERS', 'delete')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        userManagementService.delete(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully."));
    }

    @PreAuthorize("@perm.can(authentication, 'USER_MGMT_ALL_USERS', 'delete')")
    @PostMapping("/bulk-delete")
    public ResponseEntity<ApiResponse<UserManagementDto.BulkDeleteResponse>> bulkDelete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UserManagementDto.BulkDeleteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Users deleted successfully.",
                userManagementService.bulkDelete(principal.getId(), request.getIds())));
    }

    /* ---- Bulk import (Bulk Users > Import users) ---- */

    @PreAuthorize("@perm.can(authentication, 'USER_MGMT_ALL_USERS', 'update')")
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"users-import-template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(userImportService.buildTemplate());
    }

    @PreAuthorize("@perm.can(authentication, 'USER_MGMT_ALL_USERS', 'update')")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserImportDto.Result>> importUsers(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestPart("file") MultipartFile file) {
        UserImportDto.Result result = userImportService.importUsers(principal.getId(), file);
        return ResponseEntity.ok(ApiResponse.success(
                result.getImported() + " of " + result.getTotal() + " users imported.", result));
    }

    /* ---- Edit Profile of the clicked user (same shapes as /api/profile/me) ---- */

    @PreAuthorize("@perm.can(authentication, 'USER_MGMT_ALL_USERS', 'read')")
    @GetMapping("/{id}/profile")
    public ResponseEntity<ApiResponse<ProfileDto.Response>> getProfile(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile fetched successfully.", userManagementService.getProfile(principal.getId(), id)));
    }

    @PreAuthorize("@perm.can(authentication, 'USER_MGMT_ALL_USERS', 'update')")
    @PutMapping("/{id}/profile")
    public ResponseEntity<ApiResponse<ProfileDto.Response>> updateProfile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ProfileDto.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile updated successfully.",
                userManagementService.updateProfile(principal.getId(), id, request)));
    }

    @PreAuthorize("@perm.can(authentication, 'USER_MGMT_ALL_USERS', 'update')")
    @PostMapping(value = "/{id}/profile/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileDto.Response>> uploadProfilePicture(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile picture updated successfully.",
                userManagementService.uploadProfilePicture(principal.getId(), id, file)));
    }

    @PreAuthorize("@perm.can(authentication, 'USER_MGMT_ALL_USERS', 'update')")
    @PostMapping(value = "/{id}/profile/background-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileDto.Response>> uploadBackgroundPicture(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                "Background picture updated successfully.",
                userManagementService.uploadBackgroundPicture(principal.getId(), id, file)));
    }

    @PreAuthorize("@perm.can(authentication, 'USER_MGMT_ALL_USERS', 'update')")
    @DeleteMapping("/{id}/profile/picture")
    public ResponseEntity<ApiResponse<ProfileDto.Response>> removeProfilePicture(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile picture removed.", userManagementService.removeProfilePicture(principal.getId(), id)));
    }

    @PreAuthorize("@perm.can(authentication, 'USER_MGMT_ALL_USERS', 'update')")
    @DeleteMapping("/{id}/profile/background-picture")
    public ResponseEntity<ApiResponse<ProfileDto.Response>> removeBackgroundPicture(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Background picture removed.",
                userManagementService.removeBackgroundPicture(principal.getId(), id)));
    }
}