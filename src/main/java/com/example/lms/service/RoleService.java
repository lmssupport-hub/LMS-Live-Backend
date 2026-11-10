package com.example.lms.service;

import com.example.lms.dto.PackageDto;
import com.example.lms.dto.RoleDto;
import com.example.lms.entity.RoleEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.RoleRepository;
import com.example.lms.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private static final Set<String> VALID_STATUSES = Set.of("Active", "Inactive");
    private static final String ROLE_EXISTS = "Role already exists.";
    /** Names that mean "owner" in the system. A custom role may never use them (compared ignoring case, spaces, "_" and "-"). */
    private static final Set<String> RESERVED_ROLE_NAMES = Set.of("ADMIN", "SUPERADMIN");
    private static final String ROLE_RESERVED = "This role name is reserved. Please choose a different name.";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PackageService packageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /* ---------- tenant helpers ---------- */

    private Long ownerOf(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found.", HttpStatus.UNAUTHORIZED));
        return PackageService.resolveOwnerId(user);
    }

    private RoleEntity findOwned(Long id, Long ownerId) {
        // Same 404 for "doesn't exist" and "belongs to another company" -> no ID probing.
        return roleRepository.findByIdAndCreatedByAdminId(id, ownerId)
                .orElseThrow(() -> new ApiException("Role not found.", HttpStatus.NOT_FOUND));
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException("Role Name is required.", HttpStatus.BAD_REQUEST);
        }
        // trim + collapse repeated spaces: "Content   Manager " -> "Content Manager"
        String normalized = name.trim().replaceAll("\\s+", " ");
        String key = normalized.toUpperCase(java.util.Locale.ROOT).replaceAll("[\\s_\\-]+", "");
        if (RESERVED_ROLE_NAMES.contains(key)) {
            throw new ApiException(ROLE_RESERVED, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    /* ---------- package-filtered catalog (feeds the Role Permission screen) ---------- */

    @Transactional(readOnly = true)
    public List<PackageDto.FeatureCatalogEntry> getAvailableCatalog(Long userId) {
        Long ownerId = ownerOf(userId);
        return packageService.getAllowedPermissionsForOwner(ownerId).stream()
                .map(c -> new PackageDto.FeatureCatalogEntry(c.getId(), c.getName(), c.getFeatures()))
                .collect(Collectors.toList());
    }

    /** Server-side guard: a role can never contain more than the admin's package allows. */
    private void assertWithinPackage(Long ownerId, List<PackageDto.Category> requested) {
        if (requested == null) return;

        Map<String, PackageDto.Permission> allowed = new HashMap<>();
        for (PackageDto.Category c : packageService.getAllowedPermissionsForOwner(ownerId)) {
            for (PackageDto.Feature f : c.getFeatures()) {
                allowed.put(c.getId() + ":" + f.getId(), f.getPermissions());
            }
        }
        for (PackageDto.Category c : requested) {
            if (c.getFeatures() == null) continue;
            for (PackageDto.Feature f : c.getFeatures()) {
                PackageDto.Permission want = f.getPermissions();
                if (want == null) continue;
                PackageDto.Permission have = allowed.get(c.getId() + ":" + f.getId());
                boolean exceeds = (want.isCreate() && (have == null || !have.isCreate()))
                        || (want.isRead() && (have == null || !have.isRead()))
                        || (want.isUpdate() && (have == null || !have.isUpdate()))
                        || (want.isDelete() && (have == null || !have.isDelete()));
                if (exceeds) {
                    throw new ApiException(
                            "\"" + f.getName() + "\" is not included in your package.", HttpStatus.FORBIDDEN);
                }
            }
        }
    }

    /* ---------- CRUD (all owner-scoped) ---------- */

    @Transactional
    public RoleDto.Response createRole(Long userId, RoleDto.Request request) {
        Long ownerId = ownerOf(userId);
        String name = normalizeName(request.getName());

        // Scoped to THIS company only. Another company's "Instructor" never conflicts.
        if (roleRepository.existsByCreatedByAdminIdAndNameIgnoreCase(ownerId, name)) {
            throw new ApiException(ROLE_EXISTS, HttpStatus.CONFLICT);
        }
        assertWithinPackage(ownerId, request.getPermissions());

        RoleEntity entity = new RoleEntity();
        entity.setCreatedByAdminId(ownerId); // owner = company admin, even if a team member creates it
        entity.setStatus("Active");
        mapRequestToEntity(request, name, entity);
        return saveOrConflict(entity);
    }

    @Transactional
    public RoleDto.Response updateRole(Long userId, Long id, RoleDto.Request request) {
        Long ownerId = ownerOf(userId);
        RoleEntity entity = findOwned(id, ownerId);
        String name = normalizeName(request.getName());

        // Excludes this role itself (IdNot), so saving without renaming never errors.
        if (roleRepository.existsByCreatedByAdminIdAndNameIgnoreCaseAndIdNot(ownerId, name, id)) {
            throw new ApiException(ROLE_EXISTS, HttpStatus.CONFLICT);
        }
        assertWithinPackage(ownerId, request.getPermissions());

        mapRequestToEntity(request, name, entity);
        return saveOrConflict(entity);
    }

    @Transactional(readOnly = true)
    public RoleDto.Response getRoleById(Long userId, Long id) {
        return mapEntityToResponse(findOwned(id, ownerOf(userId)));
    }

    @Transactional(readOnly = true)
    public List<RoleDto.Response> getAllRoles(Long userId) {
        return roleRepository.findAllByCreatedByAdminId(ownerOf(userId)).stream()
                .map(this::mapEntityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RoleDto.Response> getActiveRoles(Long userId) {
        return roleRepository.findAllByCreatedByAdminIdAndStatus(ownerOf(userId), "Active").stream()
                .map(this::mapEntityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoleDto.Response updateStatus(Long userId, Long id, String status) {
        if (status == null || !VALID_STATUSES.contains(status)) {
            throw new ApiException("Status must be either 'Active' or 'Inactive'.", HttpStatus.BAD_REQUEST);
        }
        RoleEntity entity = findOwned(id, ownerOf(userId));
        entity.setStatus(status);
        return mapEntityToResponse(roleRepository.save(entity));
    }

    @Transactional
    public void deleteRole(Long userId, Long id) {
        roleRepository.delete(findOwned(id, ownerOf(userId)));
    }

    /* ---------- persistence + mapping ---------- */

    /**
     * saveAndFlush so a unique-constraint race (two requests at once) is caught HERE
     * and turned into a 409, instead of surfacing later as a 500 at commit time.
     */
    private RoleDto.Response saveOrConflict(RoleEntity entity) {
        try {
            return mapEntityToResponse(roleRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ROLE_EXISTS, HttpStatus.CONFLICT);
        }
    }

    private void mapRequestToEntity(RoleDto.Request request, String normalizedName, RoleEntity entity) {
        entity.setName(normalizedName);
        entity.setDescription(request.getDescription());
        try {
            entity.setPermissionsJson(objectMapper.writeValueAsString(request.getPermissions()));
        } catch (Exception e) {
            throw new ApiException("Failed to save role permissions.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private RoleDto.Response mapEntityToResponse(RoleEntity entity) {
        RoleDto.Response response = new RoleDto.Response();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        try {
            if (entity.getPermissionsJson() != null) {
                response.setPermissions(objectMapper.readValue(
                        entity.getPermissionsJson(), new TypeReference<List<PackageDto.Category>>() {}));
            }
        } catch (Exception e) {
            throw new ApiException("Failed to load role permissions.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }
}