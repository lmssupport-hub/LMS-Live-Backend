package com.example.lms.service;

import com.example.lms.dto.PackageAssignmentDto;
import com.example.lms.dto.PackageDto;
import com.example.lms.entity.PackageEntity;
import com.example.lms.entity.SystemFeatureEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.exception.RateLimitExceededException;
import com.example.lms.repository.PackageRepository;
import com.example.lms.repository.SystemFeatureRepository;
import com.example.lms.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PackageService {

    private static final Logger log = LoggerFactory.getLogger(PackageService.class);

    private final PackageRepository packageRepository;
    private final SystemFeatureRepository systemFeatureRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentHashMap<String, Instant> recentSubmissions = new ConcurrentHashMap<>();
    private static final Duration DUPLICATE_SUBMIT_WINDOW = Duration.ofSeconds(5);

    /**
     * Features that represent a single yes/no capability rather than a
     * CRUD-shaped permission (e.g. "does this package grant login access at
     * all", not "can it create/read/update/delete access records"). Mirrors
     * the frontend's SINGLE_TOGGLE_FEATURE_IDS in package.service.ts.
     *
     * The client is expected to keep all four Create/Read/Update/Delete
     * flags in lock-step for these features, but the server never trusts
     * that alone (OWASP: never rely on client-side enforcement of a
     * business rule) — validatePermissionFeaturesExist() below rejects any
     * request where they've drifted out of sync.
     */
    private static final Set<String> SINGLE_TOGGLE_FEATURE_IDS = Set.of("AUTH_USER_ACCESS");

    @Transactional
    public PackageDto.Response createPackage(PackageDto.Request request, String idempotencyKey) {
        guardAgainstDuplicateSubmit(idempotencyKey);

        if (packageRepository.existsByNameIgnoreCase(request.getName())) {
            throw new ApiException("A package with the same name already exists", HttpStatus.CONFLICT);
        }

        validatePermissionFeaturesExist(request.getPermissions());

        PackageEntity entity = new PackageEntity();
        mapRequestToEntity(request, entity);
        PackageEntity saved = packageRepository.save(entity);
        return mapEntityToResponse(saved);
    }

    @Transactional
    public PackageDto.Response updatePackage(Long id, PackageDto.Request request) {
        PackageEntity entity = packageRepository.findById(id)
                .orElseThrow(() -> new ApiException("Package not found", HttpStatus.NOT_FOUND));

        if (request.getVersion() != null && !request.getVersion().equals(entity.getVersion())) {
            throw new ApiException(
                    "This package has already been modified by another user. Please refresh and try again.",
                    HttpStatus.CONFLICT);
        }

        if (packageRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new ApiException("A package with the same name already exists", HttpStatus.CONFLICT);
        }

        validatePermissionFeaturesExist(request.getPermissions());

        mapRequestToEntity(request, entity);
        try {
            PackageEntity saved = packageRepository.save(entity);
            return mapEntityToResponse(saved);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ApiException(
                    "This package has already been modified by another user. Please refresh and try again.",
                    HttpStatus.CONFLICT);
        }
    }

    /**
     * FIX (defense-in-depth): marked read-only. This alone would NOT have
     * fixed the "Large Objects may not be used in auto-commit mode" 500 —
     * that was caused by PackageEntity.permissionsJson being mapped as
     * @Lob, which forces Hibernate to stream it as a Postgres CLOB via the
     * Large Object API regardless of the surrounding transaction's
     * autocommit setting for reads that don't otherwise need one. The real
     * fix is in PackageEntity (swap @Lob for
     * @JdbcTypeCode(SqlTypes.LONGVARCHAR)). This annotation is added
     * anyway as good practice for read paths and to guard against similar
     * lazy-load-outside-a-transaction issues in the future.
     */
    @Transactional(readOnly = true)
    public PackageDto.Response getPackageById(Long id) {
        PackageEntity entity = packageRepository.findById(id)
                .orElseThrow(() -> new ApiException("Package not found", HttpStatus.NOT_FOUND));
        return mapEntityToResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<PackageDto.Response> getPackagesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return packageRepository.findAllById(ids).stream()
                .map(this::mapEntityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<PackageDto.Response> getAllPackages(String search, String status, Pageable pageable) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        String normalizedStatus = (status == null || status.isBlank() || "All".equalsIgnoreCase(status))
                ? null
                : status.trim();

        return packageRepository.search(normalizedStatus, normalizedSearch, pageable)
                .map(this::mapEntityToResponse);
    }

    @Transactional(readOnly = true)
    public List<PackageDto.FeatureCatalogEntry> getFeatureCatalog() {
        List<SystemFeatureEntity> features = systemFeatureRepository.findByActiveTrueOrderByCategoryIdAsc();

        Map<String, List<SystemFeatureEntity>> byCategory = features.stream()
                .collect(Collectors.groupingBy(
                        SystemFeatureEntity::getCategoryId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<PackageDto.FeatureCatalogEntry> catalog = new ArrayList<>();
        for (Map.Entry<String, List<SystemFeatureEntity>> entry : byCategory.entrySet()) {
            List<SystemFeatureEntity> categoryFeatures = entry.getValue();
            String categoryName = categoryFeatures.get(0).getCategoryName();

            List<PackageDto.Feature> featureDtos = new ArrayList<>();
            for (SystemFeatureEntity f : categoryFeatures) {
                PackageDto.Feature dto = new PackageDto.Feature();
                dto.setId(f.getId());
                dto.setName(f.getName());
                dto.setPermissions(new PackageDto.Permission(false, false, false, false));
                featureDtos.add(dto);
            }
            catalog.add(new PackageDto.FeatureCatalogEntry(entry.getKey(), categoryName, featureDtos));
        }
        return catalog;
    }

    @Transactional
    public void deletePackage(Long id) {
        if (!packageRepository.existsById(id)) {
            throw new ApiException("Package not found", HttpStatus.NOT_FOUND);
        }
        packageRepository.deleteById(id);
    }

    /**
     * Doc06 S#19-22 — assigns an existing package to an already-registered
     * Admin, identified by email. Sets User.packageId (which
     * SignUpService.listAdmins()/toAdminSummary() already reads), plus
     * assigned/expiry timestamps computed from the package's billing cycle
     * so isPackageExpired() on the entity works immediately.
     */
    @Transactional
    public void assignPackageToUser(PackageAssignmentDto request) {
        PackageEntity packageEntity = packageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ApiException("Package not found", HttpStatus.NOT_FOUND));

        if (!"Active".equalsIgnoreCase(packageEntity.getStatus())) {
            throw new ApiException("This package is inactive and cannot be assigned", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findByEmailIgnoreCase(request.getEmailId())
                .orElseThrow(() -> new ApiException("No admin account found for this email", HttpStatus.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        user.setPackageId(packageEntity.getId());
        user.setPackageAssignedAt(now);
        user.setPackageExpiresAt(computeExpiry(now, packageEntity.getBillingCycle()));

        userRepository.save(user);
    }

    private LocalDateTime computeExpiry(LocalDateTime from, String billingCycle) {
        return "Yearly".equalsIgnoreCase(billingCycle) ? from.plusYears(1) : from.plusMonths(1);
    }

    /**
     * Validates that every submitted feature id is a real, active system
     * feature, and that single-toggle features (see
     * SINGLE_TOGGLE_FEATURE_IDS) weren't submitted in an inconsistent
     * half-on state. The frontend is expected to keep these features'
     * Create/Read/Update/Delete flags identical, but per OWASP guidance we
     * never rely on client-side enforcement alone for a business rule that
     * affects access control.
     */
    private void validatePermissionFeaturesExist(List<PackageDto.Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return;
        }

        Set<String> knownFeatureIds = systemFeatureRepository.findByActiveTrueOrderByCategoryIdAsc().stream()
                .map(SystemFeatureEntity::getId)
                .collect(Collectors.toSet());

        for (PackageDto.Category category : categories) {
            if (category.getFeatures() == null) {
                continue;
            }
            for (PackageDto.Feature feature : category.getFeatures()) {
                if (!knownFeatureIds.contains(feature.getId())) {
                    throw new ApiException(
                            "One or more selected features are invalid or no longer available",
                            HttpStatus.BAD_REQUEST);
                }

                if (SINGLE_TOGGLE_FEATURE_IDS.contains(feature.getId()) && feature.getPermissions() != null) {
                    PackageDto.Permission p = feature.getPermissions();
                    boolean uniform = p.isCreate() == p.isRead()
                            && p.isRead() == p.isUpdate()
                            && p.isUpdate() == p.isDelete();
                    if (!uniform) {
                        throw new ApiException(
                                "\"" + feature.getName() + "\" only supports a single access toggle",
                                HttpStatus.BAD_REQUEST);
                    }
                }
            }
        }
    }

    private void guardAgainstDuplicateSubmit(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        recentSubmissions.entrySet().removeIf(e -> Duration.between(e.getValue(), now).compareTo(DUPLICATE_SUBMIT_WINDOW) > 0);

        Instant previous = recentSubmissions.putIfAbsent(idempotencyKey, now);
        if (previous != null) {
            throw new RateLimitExceededException(
                    "This request is already being processed. Please wait a moment before retrying.");
        }
    }

    private void mapRequestToEntity(PackageDto.Request request, PackageEntity entity) {
        entity.setAvailablePackage(request.getAvailablePackage());
        entity.setName(request.getName().trim());
        entity.setDescription(request.getDescription());
        entity.setPrice(request.getPrice());
        entity.setBillingCycle(request.getBillingCycle());
        entity.setUserLimit(request.getUserLimit());
        entity.setStorageLimit(request.getStorageLimit());
        try {
            entity.setPermissionsJson(objectMapper.writeValueAsString(request.getPermissions()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize permissions", e);
        }
    }

    private PackageDto.Response mapEntityToResponse(PackageEntity entity) {
        PackageDto.Response response = new PackageDto.Response();
        response.setId(entity.getId());
        response.setAvailablePackage(entity.getAvailablePackage());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setPrice(entity.getPrice());
        response.setBillingCycle(entity.getBillingCycle());
        response.setUserLimit(entity.getUserLimit());
        response.setStorageLimit(entity.getStorageLimit());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setVersion(entity.getVersion());
        try {
            if (entity.getPermissionsJson() != null) {
                List<PackageDto.Category> categories = objectMapper.readValue(
                        entity.getPermissionsJson(), new TypeReference<List<PackageDto.Category>>() {});
                response.setPermissions(categories);
            } else {
                response.setPermissions(List.of());
            }
        } catch (Exception e) {
            // Defensive: a single row with malformed permissions_json (bad manual
            // edit, corrupt migration, etc.) should not 500 the entire list/by-ids
            // response for every other valid package. Log loudly so it's caught
            // and fixed, but degrade this one record to an empty permission set
            // instead of failing the whole request.
            log.error("Corrupt permissions_json for package id={}, returning empty permissions", entity.getId(), e);
            response.setPermissions(List.of());
        }
        return response;
    }
}