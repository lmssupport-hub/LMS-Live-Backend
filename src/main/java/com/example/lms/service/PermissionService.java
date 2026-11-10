package com.example.lms.service;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.PackageDto;
import com.example.lms.dto.PermissionDto;
import com.example.lms.entity.RoleEntity;
import com.example.lms.entity.User;
import com.example.lms.repository.RoleRepository;
import com.example.lms.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single place that answers "what may this user do?".
 *
 * <ul>
 *   <li><b>SUPER_ADMIN</b> (app owner): full access.</li>
 *   <li><b>Company ADMIN</b> (self-signup account): exactly what the company's package allows.
 *       Dashboard, Role Management and Subscription are always shown to this user
 *       (see {@link PermissionDto.Snapshot#isCompanyAdmin()}); every other menu / action is
 *       package based.</li>
 *   <li><b>Everyone else</b> (Instructor, Learner, any custom role created in Role Management):
 *       what the user's role grants, limited to what the company's package allows - so a
 *       role can never keep access to something the package no longer includes.</li>
 * </ul>
 *
 * Fail-closed: a missing, inactive, foreign-company or unreadable role, or an expired /
 * missing package, grants nothing.
 *
 * Registered as bean "perm" so controllers can write
 * {@code @PreAuthorize("@perm.can(authentication, 'FEATURE_ID', 'create')")}.
 */
@Service("perm")
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    /** Feature ids seeded by SystemFeatureSeeder. */
    public static final String COURSE_CREATE = "COURSE_MGMT_CREATE_COURSE";
    public static final String COURSE_LIST = "COURSE_MGMT_COURSE_LIST";
    public static final String COURSE_ORGANIZE = "COURSE_MGMT_ORGANIZE_MODULES";
    public static final String CONTENT_MULTIMEDIA = "CONTENT_MGMT_MULTIMEDIA";
    public static final String CONTENT_EBOOK = "CONTENT_MGMT_EBOOK";
    public static final String ENROLL_UNENROLL = "ENROLLMENT_ENROLL_UNENROLL";
    public static final String ENROLLED_LIST = "ENROLLMENT_ENROLLED_LIST";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PackageService packageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PermissionService(UserRepository userRepository,
                             RoleRepository roleRepository,
                             PackageService packageService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.packageService = packageService;
    }

    @Transactional(readOnly = true)
    public PermissionDto.Snapshot snapshotFor(Long userId) {
        return userRepository.findById(userId)
                .map(this::snapshotOf)
                .orElseGet(PermissionDto.Snapshot::none);
    }

    @Transactional(readOnly = true)
    public PermissionDto.Snapshot snapshotOf(User user) {
        String role = user.getRole() == null ? "" : user.getRole().trim().toUpperCase(Locale.ROOT);
        // Team members (invited users) always have companyAdminId set. Even if one of them ends up
        // with a role literally named ADMIN / SUPER_ADMIN, that name never makes them an owner.
        boolean teamMember = user.getCompanyAdminId() != null;

        if (!teamMember && "SUPER_ADMIN".equals(role)) {
            return PermissionDto.Snapshot.full();
        }

        Long ownerId = PackageService.resolveOwnerId(user);
        List<PermissionDto.Grant> packageGrants = packageGrantsOf(ownerId);

        if (!teamMember && "ADMIN".equals(role)) {
            return new PermissionDto.Snapshot(false, true, packageGrants);
        }

        if (user.getRoleId() == null) {
            return PermissionDto.Snapshot.none();
        }
        return roleRepository.findById(user.getRoleId())
                .filter(r -> "Active".equalsIgnoreCase(r.getStatus()))
                // a role created by another company never applies to this user
                .filter(r -> ownerId.equals(r.getCreatedByAdminId()))
                .map(r -> new PermissionDto.Snapshot(false, false, limitToPackage(grantsOf(r), packageGrants)))
                .orElseGet(PermissionDto.Snapshot::none);
    }

    /** SpEL entry point for @PreAuthorize. */
    public boolean can(Authentication authentication, String featureId, String action) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return false;
        }
        return snapshotFor(principal.getId()).allows(featureId, action);
    }

    /* ------------------------------------------------------------------ */
    /*  helpers                                                           */
    /* ------------------------------------------------------------------ */

    /** What the company's package allows (features with at least one action ON). Empty = no / expired package. */
    private List<PermissionDto.Grant> packageGrantsOf(Long ownerId) {
        try {
            List<PermissionDto.Grant> grants = new ArrayList<>();
            for (PackageDto.Category category : packageService.getAllowedPermissionsForOwner(ownerId)) {
                addGrants(category, grants);
            }
            return List.copyOf(grants);
        } catch (Exception e) {
            log.warn("Could not read package permissions for owner id={}; granting nothing.", ownerId, e);
            return List.of();
        }
    }

    /** Role grants AND package grants: an action survives only if both allow it. */
    private static List<PermissionDto.Grant> limitToPackage(List<PermissionDto.Grant> roleGrants,
                                                           List<PermissionDto.Grant> packageGrants) {
        Map<String, PermissionDto.Grant> allowed = new HashMap<>();
        for (PermissionDto.Grant g : packageGrants) {
            allowed.put(g.getFeatureId(), g);
        }
        List<PermissionDto.Grant> result = new ArrayList<>();
        for (PermissionDto.Grant r : roleGrants) {
            PermissionDto.Grant p = allowed.get(r.getFeatureId());
            if (p == null) {
                continue;
            }
            boolean create = r.isCreate() && p.isCreate();
            boolean read = r.isRead() && p.isRead();
            boolean update = r.isUpdate() && p.isUpdate();
            boolean delete = r.isDelete() && p.isDelete();
            if (create || read || update || delete) {
                result.add(new PermissionDto.Grant(r.getFeatureId(), create, read, update, delete));
            }
        }
        return List.copyOf(result);
    }

    private List<PermissionDto.Grant> grantsOf(RoleEntity role) {
        if (role.getPermissionsJson() == null || role.getPermissionsJson().isBlank()) {
            return List.of();
        }
        try {
            List<PackageDto.Category> tree = objectMapper.readValue(
                    role.getPermissionsJson(), new TypeReference<List<PackageDto.Category>>() {});
            List<PermissionDto.Grant> grants = new ArrayList<>();
            for (PackageDto.Category category : tree) {
                addGrants(category, grants);
            }
            return grants;
        } catch (Exception e) {
            log.warn("Unreadable permissions on role id={}; granting nothing.", role.getId(), e);
            return List.of();
        }
    }

    private static void addGrants(PackageDto.Category category, List<PermissionDto.Grant> into) {
        if (category.getFeatures() == null) {
            return;
        }
        for (PackageDto.Feature feature : category.getFeatures()) {
            PackageDto.Permission p = feature.getPermissions();
            if (p == null || !(p.isCreate() || p.isRead() || p.isUpdate() || p.isDelete())) {
                continue;
            }
            into.add(new PermissionDto.Grant(
                    feature.getId(), p.isCreate(), p.isRead(), p.isUpdate(), p.isDelete()));
        }
    }
}
