package com.example.lms.controller;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.PackageAssignmentDto;
import com.example.lms.dto.PackageDto;
import com.example.lms.service.PackageService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/packages")
public class PackageController {
    private final PackageService packageService;
    public PackageController(PackageService packageService) {
        this.packageService = packageService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PackageDto.Response> create(
            @Valid @RequestBody PackageDto.Request request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED).body(packageService.createPackage(request, idempotencyKey));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PackageDto.Response> update(@PathVariable Long id, @Valid @RequestBody PackageDto.Request request) {
        return ResponseEntity.ok(packageService.updatePackage(id, request));
    }

    /** Active / Inactive switch for a package. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PackageDto.Response> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody PackageDto.StatusRequest request) {
        return ResponseEntity.ok(packageService.updatePackageStatus(id, request.getStatus()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<PackageDto.Response> getById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(packageService.getPackageById(id, userIdOf(authentication), isSuperAdmin(authentication)));
    }

    /**
     * Bulk lookup so the Admin List page (User rows from SignUpController,
     * each carrying a packageId) can resolve package name/price/billingCycle
     * for every row in one round trip instead of one call per admin.
     */
    @GetMapping("/by-ids")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<PackageDto.Response>> getByIds(@RequestParam List<Long> ids, Authentication authentication) {
        return ResponseEntity.ok(packageService.getPackagesByIds(ids, userIdOf(authentication), isSuperAdmin(authentication)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Page<PackageDto.Response>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "name") Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(packageService.getAllPackages(search, status, pageable, isSuperAdmin(authentication)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        packageService.deletePackage(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/features")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<PackageDto.FeatureCatalogEntry>> getFeatureCatalog() {
        return ResponseEntity.ok(packageService.getFeatureCatalog());
    }

    @PostMapping("/assign")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> assign(@Valid @RequestBody PackageAssignmentDto request) {
        packageService.assignPackageToUser(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Company admin buys a package for THEMSELVES (Subscription -> Payment page).
     * Only the JWT user can receive the package; price/duration come from the DB.
     */
    @PostMapping("/purchase")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PackageDto.MySubscription> purchase(
            @Valid @RequestBody PackageDto.PurchaseRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(packageService.purchasePackage(userIdOf(authentication), request));
    }

    /**
     * Logged-in admin's own package countdown (header "days left" pill).
     * FIXED: the JWT filter sets an AuthPrincipal object as the principal (not an email string),
     * so authentication.getName() never matched a user. We read the userId from the principal instead.
     */
    @GetMapping("/my-subscription")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<PackageDto.MySubscription> mySubscription(Authentication authentication) {
        Long userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            userId = principal.getId();
        }
        return ResponseEntity.ok(packageService.getMySubscription(userId));
    }

    /** NEW: billing details the logged-in admin entered when subscribing (Manage Details -> Billing tab). */
    @GetMapping("/my-billing")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PackageDto.MyBilling> myBilling(Authentication authentication) {
        return ResponseEntity.ok(packageService.getMyBilling(userIdOf(authentication)));
    }

    /** Assigned / expiry dates of the admins on this package. */
    @GetMapping("/{packageId}/admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<PackageDto.AdminAssignment>> assignments(@PathVariable Long packageId) {
        return ResponseEntity.ok(packageService.getAssignments(packageId));
    }

    /** Super Admin removes a package from one admin ("Unassign" button). */
    @DeleteMapping("/{packageId}/admins/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> unassign(@PathVariable Long packageId, @PathVariable Long userId) {
        packageService.unassignPackageFromUser(packageId, userId);
        return ResponseEntity.noContent().build();
    }

    private static Long userIdOf(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.getId();
        }
        return null;
    }

    private static boolean isSuperAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }
}