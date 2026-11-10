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
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Single place that answers "what may this user do?".
 *
 * Company ADMIN and SUPER_ADMIN keep full access (as before). Every other user (invited team
 * member / learner) gets exactly what the role chosen in Role Management grants, and nothing
 * else. Fail-closed: a missing, inactive, foreign-company or unreadable role grants nothing.
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

    private static final Set<String> FULL_ACCESS_ROLES = Set.of("ADMIN", "SUPER_ADMIN");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PermissionService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
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
        if (FULL_ACCESS_ROLES.contains(role)) {
            return PermissionDto.Snapshot.full();
        }
        if (user.getRoleId() == null) {
            return PermissionDto.Snapshot.none();
        }
        Long ownerId = PackageService.resolveOwnerId(user);
        return roleRepository.findById(user.getRoleId())
                .filter(r -> "Active".equalsIgnoreCase(r.getStatus()))
                // a role created by another company never applies to this user
                .filter(r -> ownerId.equals(r.getCreatedByAdminId()))
                .map(this::grantsOf)
                .orElseGet(PermissionDto.Snapshot::none);
    }

    /** SpEL entry point for @PreAuthorize. */
    public boolean can(Authentication authentication, String featureId, String action) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return false;
        }
        return snapshotFor(principal.getId()).allows(featureId, action);
    }

    private PermissionDto.Snapshot grantsOf(RoleEntity role) {
        if (role.getPermissionsJson() == null || role.getPermissionsJson().isBlank()) {
            return PermissionDto.Snapshot.none();
        }
        try {
            List<PackageDto.Category> tree = objectMapper.readValue(
                    role.getPermissionsJson(), new TypeReference<List<PackageDto.Category>>() {});
            List<PermissionDto.Grant> grants = new ArrayList<>();
            for (PackageDto.Category category : tree) {
                if (category.getFeatures() == null) {
                    continue;
                }
                for (PackageDto.Feature feature : category.getFeatures()) {
                    PackageDto.Permission p = feature.getPermissions();
                    if (p == null || !(p.isCreate() || p.isRead() || p.isUpdate() || p.isDelete())) {
                        continue;
                    }
                    grants.add(new PermissionDto.Grant(
                            feature.getId(), p.isCreate(), p.isRead(), p.isUpdate(), p.isDelete()));
                }
            }
            return new PermissionDto.Snapshot(false, List.copyOf(grants));
        } catch (Exception e) {
            log.warn("Unreadable permissions on role id={}; granting nothing.", role.getId(), e);
            return PermissionDto.Snapshot.none();
        }
    }
}
