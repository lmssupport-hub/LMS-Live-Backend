package com.example.lms.controller;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.PackageDto;
import com.example.lms.dto.RoleDto;
import com.example.lms.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class RoleController {

    private final RoleService roleService;

    /** Features/actions THIS admin's package allows. The Role Permission screen is built from this. */
    @GetMapping("/features")
    public ResponseEntity<List<PackageDto.FeatureCatalogEntry>> features(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(roleService.getAvailableCatalog(principal.getId()));
    }

    @PostMapping
    public ResponseEntity<RoleDto.Response> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody RoleDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(principal.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleDto.Response> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody RoleDto.Request request) {
        return ResponseEntity.ok(roleService.updateRole(principal.getId(), id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleDto.Response> getById(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRoleById(principal.getId(), id));
    }

    // Only the logged-in admin's own roles (tenant-scoped)
    @GetMapping
    public ResponseEntity<List<RoleDto.Response>> getAll(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(roleService.getAllRoles(principal.getId()));
    }

    // Invite dropdown: this admin's active roles only
    @GetMapping("/active")
    public ResponseEntity<List<RoleDto.Response>> getActive(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(roleService.getActiveRoles(principal.getId()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<RoleDto.Response> updateStatus(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody RoleDto.StatusUpdateRequest request) {
        return ResponseEntity.ok(roleService.updateStatus(principal.getId(), id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        roleService.deleteRole(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}