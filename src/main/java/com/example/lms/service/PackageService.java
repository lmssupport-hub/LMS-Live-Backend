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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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

    /** Category id (from SystemFeatureSeeder) that controls login access. */
    public static final String AUTHENTICATION_CATEGORY_ID = "AUTHENTICATION";

    /** Package type value for free packages. The other type is "Price". */
    private static final String FREE_PACKAGE = "Free";

    /** Monthly = 30 days, Yearly = 365 days. Free (Custom) uses the days the user entered. */
    private static final int MONTHLY_DAYS = 30;
    private static final int YEARLY_DAYS = 365;

    private final PackageRepository packageRepository;
    private final SystemFeatureRepository systemFeatureRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentHashMap<String, Instant> recentSubmissions = new ConcurrentHashMap<>();
    private static final Duration DUPLICATE_SUBMIT_WINDOW = Duration.ofSeconds(5);

    private static final Set<String> SINGLE_TOGGLE_FEATURE_IDS = Set.of("AUTH_USER_ACCESS");

    /* ------------------------------------------------------------------ */
    /*  Login access (used by LoginService)                               */
    /* ------------------------------------------------------------------ */

    /**
     * True only if the package exists, is Active, and its AUTHENTICATION
     * category is enabled. Fails CLOSED: null id, missing package, inactive
     * package, or missing/corrupt permissions JSON all return false.
     */
    @Transactional(readOnly = true)
    public boolean hasLoginAccess(Long packageId) {
        if (packageId == null) {
            return false;
        }
        return packageRepository.findById(packageId)
                .filter(p -> "Active".equalsIgnoreCase(p.getStatus()))
                .map(p -> isCategoryEnabled(p.getPermissionsJson(), AUTHENTICATION_CATEGORY_ID))
                .orElse(false);
    }

    private boolean isCategoryEnabled(String permissionsJson, String categoryId) {
        if (permissionsJson == null || permissionsJson.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(permissionsJson);
            if (!root.isArray()) {
                return false;
            }
            for (JsonNode category : root) {
                if (categoryId.equals(category.path("id").asText())) {
                    return category.path("enabled").asBoolean(false);
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Corrupt permissions_json while checking '{}' access", categoryId, e);
        }
        return false;
    }

    /* ------------------------------------------------------------------ */
    /*  Package CRUD                                                      */
    /* ------------------------------------------------------------------ */

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
     * Active <-> Inactive switch. Touches ONLY the status column; name, price,
     * permissions etc. stay exactly as saved. Users on an Inactive package lose
     * login access (see hasLoginAccess) and the package can no longer be assigned.
     */
    @Transactional
    public PackageDto.Response updatePackageStatus(Long id, String status) {
        PackageEntity entity = packageRepository.findById(id)
                .orElseThrow(() -> new ApiException("Package not found", HttpStatus.NOT_FOUND));

        entity.setStatus("Inactive".equalsIgnoreCase(status) ? "Inactive" : "Active");
        try {
            PackageEntity saved = packageRepository.save(entity);
            return mapEntityToResponse(saved);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ApiException(
                    "This package has already been modified by another user. Please refresh and try again.",
                    HttpStatus.CONFLICT);
        }
    }

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
        List<SystemFeatureEntity> features = systemFeatureRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();

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
        user.setPackageExpiresAt(now.plusDays(effectiveDays(packageEntity)));

        userRepository.save(user);
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    private int effectiveDays(PackageEntity entity) {
        if (entity.getDurationDays() != null && entity.getDurationDays() > 0) {
            return entity.getDurationDays();
        }
        return "Yearly".equalsIgnoreCase(entity.getBillingCycle()) ? YEARLY_DAYS : MONTHLY_DAYS;
    }

    private int resolveDurationDays(PackageDto.Request request) {
        String cycle = request.getBillingCycle();
        if ("Monthly".equals(cycle)) {
            return MONTHLY_DAYS;
        }
        if ("Yearly".equals(cycle)) {
            return YEARLY_DAYS;
        }
        Integer entered = request.getDurationDays();
        if (entered == null || entered < 1) {
            throw new ApiException("Please enter the number of days", HttpStatus.BAD_REQUEST);
        }
        return entered;
    }

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
        boolean free = FREE_PACKAGE.equals(request.getAvailablePackage());

        entity.setAvailablePackage(request.getAvailablePackage());
        entity.setName(request.getName().trim());
        entity.setDescription(request.getDescription());
        entity.setPrice(free ? 0.0 : request.getPrice());
        entity.setBillingCycle(request.getBillingCycle());
        entity.setDurationDays(resolveDurationDays(request));
        entity.setUserLimit(request.getUserLimit());
        entity.setStorageLimit(request.getStorageLimit());
        // Free -> only "Auto-assign on signup" applies; Price -> only "Show on website" applies.
        entity.setAutoAssignOnSignup(free && request.isAutoAssignOnSignup());
        entity.setShowOnWebsite(!free && request.isShowOnWebsite());
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
        response.setDurationDays(effectiveDays(entity));
        response.setUserLimit(entity.getUserLimit());
        response.setStorageLimit(entity.getStorageLimit());
        response.setAutoAssignOnSignup(entity.isAutoAssignOnSignup());
        response.setShowOnWebsite(entity.isShowOnWebsite());
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
            log.error("Corrupt permissions_json for package id={}, returning empty permissions", entity.getId(), e);
            response.setPermissions(List.of());
        }
        return response;
    }
}