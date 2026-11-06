package com.example.lms.service;

import com.example.lms.dto.PackageAssignmentDto;
import com.example.lms.dto.PackageDto;
import com.example.lms.entity.BillingDetailsEntity;
import com.example.lms.entity.PackageEntity;
import com.example.lms.entity.PackagePurchaseEntity;
import com.example.lms.entity.SystemFeatureEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.exception.RateLimitExceededException;
import com.example.lms.repository.BillingDetailsRepository;
import com.example.lms.repository.PackagePurchaseRepository;
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
import org.springframework.beans.factory.annotation.Value;
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
    /** Permanent revenue ledger (one row per package assignment). */
    private final PackagePurchaseRepository purchaseRepository;
    /** NEW: latest billing details entered on the Payment page (one row per admin). */
    private final BillingDetailsRepository billingRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentHashMap<String, Instant> recentSubmissions = new ConcurrentHashMap<>();
    private static final Duration DUPLICATE_SUBMIT_WINDOW = Duration.ofSeconds(5);

    private static final Set<String> SINGLE_TOGGLE_FEATURE_IDS = Set.of("AUTH_USER_ACCESS");

    /* ------------------------------------------------------------------ */
    /*  Who is package-gated                                              */
    /* ------------------------------------------------------------------ */

    /**
     * Only the company's own ADMIN (self-signup, companyAdminId == null) is package-gated.
     * SUPER_ADMIN is never gated; invited team members are controlled by Role Management.
     * Single source of truth: LoginService, JwtAuthenticationFilter and assign all use this.
     */
    public static boolean isPackageGatedAdmin(User user) {
        return "ADMIN".equalsIgnoreCase(user.getRole()) && user.getCompanyAdminId() == null;
    }

    /** Tenant owner of any user: the company admin for team members, the user itself otherwise. */
    public static Long resolveOwnerId(User user) {
        return user.getCompanyAdminId() != null ? user.getCompanyAdminId() : user.getId();
    }

    /* ------------------------------------------------------------------ */
    /*  Login access (used by LoginService + JwtAuthenticationFilter)     */
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
    /*  Package-allowed permissions (used by Role Management)             */
    /* ------------------------------------------------------------------ */

    /**
     * What this company admin's package allows, keeping only features that have
     * at least one action ON. Fails CLOSED: no package / inactive / expired -> empty.
     * SUPER_ADMIN is not package-gated -> every feature, every action.
     */
    @Transactional(readOnly = true)
    public List<PackageDto.Category> getAllowedPermissionsForOwner(Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ApiException("Admin not found", HttpStatus.NOT_FOUND));

        if ("SUPER_ADMIN".equalsIgnoreCase(owner.getRole())) {
            return getFeatureCatalog().stream().map(entry -> {
                PackageDto.Category c = new PackageDto.Category();
                c.setId(entry.getCategoryId());
                c.setName(entry.getCategoryName());
                c.setEnabled(true);
                c.setFeatures(entry.getFeatures().stream().map(f -> {
                    PackageDto.Feature nf = new PackageDto.Feature();
                    nf.setId(f.getId());
                    nf.setName(f.getName());
                    nf.setPermissions(new PackageDto.Permission(true, true, true, true));
                    return nf;
                }).collect(Collectors.toList()));
                return c;
            }).collect(Collectors.toList());
        }

        if (owner.getPackageId() == null || owner.isPackageExpired()) {
            return List.of();
        }
        PackageEntity pkg = packageRepository.findById(owner.getPackageId())
                .filter(p -> "Active".equalsIgnoreCase(p.getStatus()))
                .orElse(null);
        if (pkg == null || pkg.getPermissionsJson() == null || pkg.getPermissionsJson().isBlank()) {
            return List.of();
        }

        List<PackageDto.Category> categories;
        try {
            categories = objectMapper.readValue(pkg.getPermissionsJson(),
                    new TypeReference<List<PackageDto.Category>>() {});
        } catch (Exception e) {
            log.error("Corrupt permissions_json for package id={}", pkg.getId(), e);
            return List.of();
        }

        List<PackageDto.Category> result = new ArrayList<>();
        for (PackageDto.Category c : categories) {
            if (c.getFeatures() == null) continue;
            List<PackageDto.Feature> kept = c.getFeatures().stream()
                    .filter(f -> f.getPermissions() != null && anyOn(f.getPermissions()))
                    .collect(Collectors.toList());
            if (kept.isEmpty()) continue;
            c.setFeatures(kept);
            c.setEnabled(true);
            result.add(c);
        }
        return result;
    }

    private static boolean anyOn(PackageDto.Permission p) {
        return p.isCreate() || p.isRead() || p.isUpdate() || p.isDelete();
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
        ensureSingleAutoAssign(entity);
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
        ensureSingleAutoAssign(entity);
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
     * Active <-> Inactive switch. Touches ONLY the status column. Users on an
     * Inactive package lose access (see hasLoginAccess) and it can't be assigned.
     */
    @Transactional
    public PackageDto.Response updatePackageStatus(Long id, String status) {
        PackageEntity entity = packageRepository.findById(id)
                .orElseThrow(() -> new ApiException("Package not found", HttpStatus.NOT_FOUND));

        entity.setStatus("Inactive".equalsIgnoreCase(status) ? "Inactive" : "Active");
        ensureSingleAutoAssign(entity);
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
     * A company ADMIN may only see packages the Super Admin published on the website
     * (Active + "Price" + showOnWebsite = true) and the package they already own.
     * Super Admin sees everything. Fails closed: hidden packages look like "not found".
     */
    private boolean isVisibleToAdmin(PackageEntity p, Long viewerPackageId) {
        boolean published = p.isShowOnWebsite()
                && "Active".equalsIgnoreCase(p.getStatus())
                && "Price".equals(p.getAvailablePackage());
        boolean own = viewerPackageId != null && viewerPackageId.equals(p.getId());
        return published || own;
    }

    private Long packageIdOf(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getPackageId).orElse(null);
    }

    @Transactional(readOnly = true)
    public PackageDto.Response getPackageById(Long id, Long viewerUserId, boolean superAdmin) {
        PackageEntity entity = packageRepository.findById(id)
                .orElseThrow(() -> new ApiException("Package not found", HttpStatus.NOT_FOUND));
        if (!superAdmin && !isVisibleToAdmin(entity, packageIdOf(viewerUserId))) {
            throw new ApiException("Package not found", HttpStatus.NOT_FOUND);
        }
        return mapEntityToResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<PackageDto.Response> getPackagesByIds(List<Long> ids, Long viewerUserId, boolean superAdmin) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Long ownPackageId = superAdmin ? null : packageIdOf(viewerUserId);
        return packageRepository.findAllById(ids).stream()
                .filter(p -> superAdmin || isVisibleToAdmin(p, ownPackageId))
                .map(this::mapEntityToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Super Admin: every package (optional status/search filter).
     * Company ADMIN: only Active + Price + showOnWebsite packages - the status filter
     * from the client is ignored for them, so they can never list hidden or inactive plans.
     */
    @Transactional(readOnly = true)
    public Page<PackageDto.Response> getAllPackages(String search, String status, Pageable pageable, boolean superAdmin) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        String normalizedStatus = (status == null || status.isBlank() || "All".equalsIgnoreCase(status))
                ? null
                : status.trim();
        if (!superAdmin) {
            normalizedStatus = "Active";
        }

        return packageRepository.search(normalizedStatus, normalizedSearch, !superAdmin, pageable)
                .map(this::mapEntityToResponse);
    }

    /** Full system feature list (all-false flags). Used by the Super Admin package permission step. */
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

    /**
     * Deletes the package AND clears it from every admin who was on it,
     * so no user is left with a dangling packageId.
     * Revenue history (package_purchases) is NOT touched - it is permanent.
     */
    @Transactional
    public void deletePackage(Long id) {
        if (!packageRepository.existsById(id)) {
            throw new ApiException("Package not found", HttpStatus.NOT_FOUND);
        }
        List<User> assigned = userRepository.findByPackageId(id);
        assigned.forEach(this::clearPackage);
        userRepository.saveAll(assigned);

        packageRepository.deleteById(id);
    }

    /* ------------------------------------------------------------------ */
    /*  Assign / Unassign / Expire / Auto-assign                          */
    /* ------------------------------------------------------------------ */

    @Transactional
    public void assignPackageToUser(PackageAssignmentDto request) {
        PackageEntity packageEntity = packageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ApiException("Package not found", HttpStatus.NOT_FOUND));

        if (!"Active".equalsIgnoreCase(packageEntity.getStatus())) {
            throw new ApiException("This package is inactive and cannot be assigned", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findByEmailIgnoreCase(request.getEmailId())
                .orElseThrow(() -> new ApiException("No admin account found for this email", HttpStatus.NOT_FOUND));

        if (!isPackageGatedAdmin(user)) {
            throw new ApiException(
                    "Packages can only be assigned to company admin accounts.", HttpStatus.BAD_REQUEST);
        }

        applyPackage(user, packageEntity);
        userRepository.save(user);
    }

    /* ------------------------------------------------------------------ */
    /*  Admin buys a package for themselves (Subscription -> Payment)     */
    /* ------------------------------------------------------------------ */

    /** TEMP test gateway. Set payment.test-mode=true only in dev/staging; defaults to OFF (fails closed). */
    @Value("${payment.test-mode:false}")
    private boolean paymentTestMode;

    private static final String TEST_CARD_NUMBER = "1111111111111111";
    private static final String TEST_CARD_CVC = "123";
    private static final String TEST_OTP = "111111";

    private static final int MAX_FAILED_PAYMENTS = 5;
    private static final Duration FAILED_PAYMENT_WINDOW = Duration.ofMinutes(10);
    /** userId -> {failure count, window start}. Stops brute-forcing the OTP / card check. */
    private final ConcurrentHashMap<Long, long[]> failedPayments = new ConcurrentHashMap<>();

    /**
     * The logged-in company admin buys a package. The server decides everything:
     *  - the user comes from the JWT (never from the body),
     *  - the package must be Active, "Price" type and showOnWebsite = true,
     *  - the price/duration come from the DB (never from the client),
     *  - payment must pass the (test) gateway check.
     * On success the billing details are saved, the package is assigned and a
     * package_purchases ledger row is written (all in one transaction).
     */
    @Transactional
    public PackageDto.MySubscription purchasePackage(Long userId, PackageDto.PurchaseRequest request) {
        if (userId == null) {
            throw new ApiException("Authentication is required to access this resource.", HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("Admin not found", HttpStatus.NOT_FOUND));
        if (!isPackageGatedAdmin(user)) {
            throw new ApiException("Only company admin accounts can buy a package.", HttpStatus.FORBIDDEN);
        }

        PackageEntity pkg = packageRepository.findById(request.getPackageId())
                .filter(p -> isVisibleToAdmin(p, null))   // published + Active + Price only
                .orElseThrow(() -> new ApiException("Package not found", HttpStatus.NOT_FOUND));

        verifyTestPayment(userId, request);

        // NEW: only reached after a successful payment check, so a failed payment never saves billing.
        saveBillingDetails(user, request.getBilling());

        applyPackage(user, pkg);
        userRepository.save(user);
        failedPayments.remove(userId);
        return getMySubscription(userId);
    }

    private void verifyTestPayment(Long userId, PackageDto.PurchaseRequest request) {
        if (!paymentTestMode) {
            // No real gateway connected and test mode is off -> never grant a package for free.
            throw new ApiException("Online payment is not available right now.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        long now = System.currentTimeMillis();
        long[] state = failedPayments.compute(userId, (k, v) ->
                (v == null || now - v[1] > FAILED_PAYMENT_WINDOW.toMillis()) ? new long[]{0, now} : v);
        if (state[0] >= MAX_FAILED_PAYMENTS) {
            throw new RateLimitExceededException("Too many failed payment attempts. Please try again later.");
        }

        String card = request.getCardNumber().replaceAll("\\s", "");
        boolean ok = constantTimeEquals(card, TEST_CARD_NUMBER)
                & constantTimeEquals(request.getCvc(), TEST_CARD_CVC)
                & constantTimeEquals(request.getOtp(), TEST_OTP);
        if (!ok) {
            state[0]++;
            throw new ApiException("Payment failed. Please check your card details and verification code.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /* ------------------------------------------------------------------ */
    /*  Billing details (Manage Details -> Billing Details tab)           */
    /* ------------------------------------------------------------------ */

    /** Upserts the admin's latest billing details (one row per user). No card data here. */
    private void saveBillingDetails(User user, PackageDto.BillingRequest b) {
        BillingDetailsEntity entity = billingRepository.findByUserId(user.getId())
                .orElseGet(BillingDetailsEntity::new);
        entity.setUserId(user.getId());
        entity.setBillingName(resolveBillingName(b, user));
        entity.setCompanyName(blankToNull(b.getCompanyName()));
        entity.setEmail(b.getEmail().trim());
        entity.setAddress(b.getAddress().trim());
        entity.setCity(b.getCity().trim());
        entity.setState(blankToNull(b.getState()));
        entity.setPostalCode(b.getPostalCode().trim());
        entity.setCountry(b.getCountry().trim());
        entity.setVatNumber(blankToNull(b.getVatNumber()));
        billingRepository.save(entity);
    }

    /** "First Last" from the billing form; falls back to the account name if both are empty. */
    private String resolveBillingName(PackageDto.BillingRequest b, User user) {
        String first = b.getFirstName() == null ? "" : b.getFirstName().trim();
        String last = b.getLastName() == null ? "" : b.getLastName().trim();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? (user.getFirstName() + " " + user.getLastName()).trim() : name;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Billing details of the logged-in admin. Empty (all null) when nothing was saved yet. */
    @Transactional(readOnly = true)
    public PackageDto.MyBilling getMyBilling(Long userId) {
        PackageDto.MyBilling empty = new PackageDto.MyBilling(null, null, null, null, null, null, null, null, null);
        if (userId == null) {
            return empty;
        }
        return billingRepository.findByUserId(userId)
                .map(e -> new PackageDto.MyBilling(
                        e.getBillingName(), e.getCompanyName(), e.getEmail(), e.getAddress(),
                        e.getCity(), e.getState(), e.getPostalCode(), e.getCountry(), e.getVatNumber()))
                .orElse(empty);
    }

    /**
     * Called right after a new admin signs up. If there is a Free package with
     * "Auto-assign on signup" ON and status Active, the new admin gets it.
     * If none exists, nothing happens (Super Admin can assign manually later).
     * Runs inside the signup transaction, so no separate @Transactional.
     */
    public void assignAutoPackageOnSignup(User user) {
        if (!isPackageGatedAdmin(user)) {
            return;
        }
        packageRepository
                .findFirstByAvailablePackageAndAutoAssignOnSignupTrueAndStatusIgnoreCaseOrderByIdAsc(
                        FREE_PACKAGE, "Active")
                .ifPresent(pkg -> {
                    applyPackage(user, pkg);
                    userRepository.save(user);
                });
    }

    /** Super Admin manually removes the package from ONE admin ("Unassign" button). */
    @Transactional
    public void unassignPackageFromUser(Long packageId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("Admin not found", HttpStatus.NOT_FOUND));

        if (user.getPackageId() == null || !user.getPackageId().equals(packageId)) {
            throw new ApiException("This admin is not assigned to this package", HttpStatus.BAD_REQUEST);
        }

        clearPackage(user);
        userRepository.save(user);
    }

    /**
     * Called by PackageExpiryScheduler: removes the package from every admin
     * whose duration has ended (packageExpiresAt <= now).
     */
    @Transactional
    public int expireDuePackages() {
        List<User> due = userRepository
                .findByPackageIdIsNotNullAndPackageExpiresAtLessThanEqual(LocalDateTime.now());
        if (due.isEmpty()) {
            return 0;
        }
        due.forEach(this::clearPackage);
        userRepository.saveAll(due);
        log.info("Expired packages removed from {} admin(s)", due.size());
        return due.size();
    }

    /** Assigned / expiry dates of every admin on this package (for the remaining-days display). */
    @Transactional(readOnly = true)
    public List<PackageDto.AdminAssignment> getAssignments(Long packageId) {
        if (!packageRepository.existsById(packageId)) {
            throw new ApiException("Package not found", HttpStatus.NOT_FOUND);
        }
        return userRepository.findByPackageId(packageId).stream()
                .map(u -> new PackageDto.AdminAssignment(u.getId(), u.getPackageAssignedAt(), u.getPackageExpiresAt()))
                .collect(Collectors.toList());
    }

    /** Package countdown for the logged-in admin (header "days left" pill + Manage Details). */
    @Transactional(readOnly = true)
    public PackageDto.MySubscription getMySubscription(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (user == null || user.getPackageId() == null) {
            return new PackageDto.MySubscription(null, null, null, null, now);
        }
        String name = packageRepository.findById(user.getPackageId())
                .map(PackageEntity::getName)
                .orElse(null);
        return new PackageDto.MySubscription(
                user.getPackageId(), name, user.getPackageAssignedAt(), user.getPackageExpiresAt(), now);
    }

    /**
     * Gives the package to the user AND writes a permanent row in package_purchases
     * (date + price snapshot). That ledger is what the Super Admin revenue chart reads,
     * so revenue stays even after the package expires, is unassigned or is deleted.
     * Always runs inside the caller's transaction (assign / signup).
     */
    private void applyPackage(User user, PackageEntity pkg) {
        LocalDateTime now = LocalDateTime.now();
        user.setPackageId(pkg.getId());
        user.setPackageAssignedAt(now);
        user.setPackageExpiresAt(now.plusDays(effectiveDays(pkg)));

        PackagePurchaseEntity purchase = new PackagePurchaseEntity();
        purchase.setUserId(user.getId());
        purchase.setPackageId(pkg.getId());
        purchase.setPackageName(pkg.getName());
        purchase.setPrice(pkg.getPrice());
        purchase.setPurchasedAt(now);
        purchaseRepository.save(purchase);
    }

    private void clearPackage(User user) {
        user.setPackageId(null);
        user.setPackageAssignedAt(null);
        user.setPackageExpiresAt(null);
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    /** Rendu Active package "auto-assign on signup" la irukka koodadhu. */
    private void ensureSingleAutoAssign(PackageEntity entity) {
        if (!entity.isAutoAssignOnSignup() || !"Active".equalsIgnoreCase(entity.getStatus())) {
            return;
        }
        Long selfId = entity.getId() == null ? -1L : entity.getId();
        if (packageRepository.existsByAutoAssignOnSignupTrueAndStatusIgnoreCaseAndIdNot("Active", selfId)) {
            throw new ApiException(
                    "Another active package is already set to auto-assign on signup.", HttpStatus.CONFLICT);
        }
    }

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
        // Status toggle from the create/edit modal (null = keep current; new packages default to Active).
        if (request.getStatus() != null) {
            entity.setStatus("Inactive".equalsIgnoreCase(request.getStatus()) ? "Inactive" : "Active");
        }
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