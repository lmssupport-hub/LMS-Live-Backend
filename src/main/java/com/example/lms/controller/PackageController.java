package com.example.lms.controller;
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

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<PackageDto.Response> getById(@PathVariable Long id) {
        return ResponseEntity.ok(packageService.getPackageById(id));
    }

    /**
     * Bulk lookup so the Admin List page (User rows from SignUpController,
     * each carrying a packageId) can resolve package name/price/billingCycle
     * for every row in one round trip instead of one call per admin.
     */
    @GetMapping("/by-ids")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<PackageDto.Response>> getByIds(@RequestParam List<Long> ids) {
        return ResponseEntity.ok(packageService.getPackagesByIds(ids));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Page<PackageDto.Response>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(packageService.getAllPackages(search, status, pageable));
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
}