package com.example.lms.service;

import com.example.lms.dto.ProfileDto;
import com.example.lms.dto.UserManagementDto;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "All Users" page: list / status / delete / edit-profile of the team members of ONE company.
 *
 * Rules enforced here:
 *  - WHO may use which action is decided by the package / role permission on the controller
 *    (USER_MGMT_ALL_USERS read / update / delete);
 *  - every target user must belong to the caller's company (404 otherwise - same answer for
 *    "does not exist" and "belongs to someone else", so ids cannot be probed). The company ADMIN
 *    is never a target (it has no companyAdminId);
 *  - the caller can never change their own status or delete themselves.
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final String ACTIVE = "Active";
    private static final String INACTIVE = "Inactive";
    private static final String SUSPENDED = "Suspended";

    private final UserRepository userRepository;
    private final ProfileService profileService;

    /* ------------------------------------------------------------------ */
    /*  List                                                              */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public UserManagementDto.PageResponse list(Long actorId, String search, String status, int page, int size) {
        User actor = requireManager(actorId);

        String statusFilter = normaliseStatusFilter(status);
        String term = search == null ? null : search.trim();
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        Page<User> result = userRepository.searchManagedUsers(
                PackageService.resolveOwnerId(actor),
                term == null || term.isEmpty() ? null : term,
                statusFilter,
                LocalDate.now(),
                PageRequest.of(safePage, safeSize,
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));

        LocalDate today = LocalDate.now();
        return UserManagementDto.PageResponse.builder()
                .content(result.getContent().stream().map(u -> toRow(u, today)).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    /* ------------------------------------------------------------------ */
    /*  Status (Active / Inactive / Suspended)                            */
    /* ------------------------------------------------------------------ */

    @Transactional
    public UserManagementDto.Row updateStatus(Long actorId, Long targetId, UserManagementDto.StatusRequest req) {
        User actor = requireManager(actorId);
        User target = findManaged(actor, targetId);
        rejectSelf(actor, target, "You cannot change your own status.");

        LocalDate today = LocalDate.now();
        switch (req.getStatus()) {
            case ACTIVE -> {
                target.setActive(true);
                clearSuspension(target);
            }
            case INACTIVE -> {
                target.setActive(false);
                clearSuspension(target);
                forceOffline(target);
            }
            case SUSPENDED -> {
                LocalDate from = req.getSuspendFrom();
                LocalDate to = req.getSuspendTo();
                if (from == null || to == null) {
                    throw new ApiException("Suspension start and end dates are required.", HttpStatus.BAD_REQUEST);
                }
                if (to.isBefore(from)) {
                    throw new ApiException("Suspension end date cannot be before the start date.", HttpStatus.BAD_REQUEST);
                }
                if (to.isBefore(today)) {
                    throw new ApiException("Suspension end date cannot be in the past.", HttpStatus.BAD_REQUEST);
                }
                target.setActive(true);
                target.setSuspendedFrom(from);
                target.setSuspendedTo(to);
                if (target.isSuspendedOn(today)) {
                    forceOffline(target);
                }
            }
            default -> throw new ApiException("Status must be Active, Inactive or Suspended.", HttpStatus.BAD_REQUEST);
        }

        userRepository.save(target);
        log.info("User {} set to {} by {}", target.getId(), req.getStatus(), actor.getId());
        return toRow(target, today);
    }

    /* ------------------------------------------------------------------ */
    /*  Delete (soft)                                                     */
    /* ------------------------------------------------------------------ */

    @Transactional
    public void delete(Long actorId, Long targetId) {
        User actor = requireManager(actorId);
        User target = findManaged(actor, targetId);
        rejectSelf(actor, target, "You cannot delete your own account.");
        softDelete(target);
        log.info("User {} deleted by {}", target.getId(), actor.getId());
    }

    @Transactional
    public UserManagementDto.BulkDeleteResponse bulkDelete(Long actorId, List<Long> ids) {
        User actor = requireManager(actorId);
        Set<Long> requested = new HashSet<>(ids);
        if (requested.contains(actor.getId())) {
            throw new ApiException("You cannot delete your own account.", HttpStatus.BAD_REQUEST);
        }

        List<User> owned = userRepository.findByIdInAndCompanyAdminIdAndDeletedAtIsNull(
                requested, PackageService.resolveOwnerId(actor));
        Set<Long> deletedIds = new HashSet<>();
        for (User u : owned) {
            softDelete(u);
            deletedIds.add(u.getId());
        }

        List<Long> skipped = new ArrayList<>(requested);
        skipped.removeAll(deletedIds);
        log.info("{} user(s) bulk-deleted by {}", deletedIds.size(), actor.getId());
        return UserManagementDto.BulkDeleteResponse.builder()
                .deleted(deletedIds.size())
                .skipped(skipped)
                .build();
    }

    /* ------------------------------------------------------------------ */
    /*  Edit Profile of ANOTHER user (reuses ProfileService)              */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public ProfileDto.Response getProfile(Long actorId, Long targetId) {
        User target = findManaged(requireManager(actorId), targetId);
        return profileService.getMyProfile(target.getId());
    }

    @Transactional
    public ProfileDto.Response updateProfile(Long actorId, Long targetId, ProfileDto.UpdateRequest req) {
        User target = findManaged(requireManager(actorId), targetId);
        return profileService.updateProfile(target.getId(), req, true);
    }

    @Transactional
    public ProfileDto.Response uploadProfilePicture(Long actorId, Long targetId, MultipartFile file) {
        User target = findManaged(requireManager(actorId), targetId);
        return profileService.updateProfilePicture(target.getId(), file);
    }

    @Transactional
    public ProfileDto.Response uploadBackgroundPicture(Long actorId, Long targetId, MultipartFile file) {
        User target = findManaged(requireManager(actorId), targetId);
        return profileService.updateBackgroundPicture(target.getId(), file);
    }

    @Transactional
    public ProfileDto.Response removeProfilePicture(Long actorId, Long targetId) {
        User target = findManaged(requireManager(actorId), targetId);
        return profileService.removeProfilePicture(target.getId());
    }

    @Transactional
    public ProfileDto.Response removeBackgroundPicture(Long actorId, Long targetId) {
        User target = findManaged(requireManager(actorId), targetId);
        return profileService.removeBackgroundPicture(target.getId());
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    /** The logged-in user (their company is derived from it). */
    private User requireManager(Long actorId) {
        return userRepository.findById(actorId)
                .orElseThrow(() -> new ApiException("User not found.", HttpStatus.UNAUTHORIZED));
    }

    /** Same 404 for "does not exist", "already deleted" and "another company's user". */
    private User findManaged(User actor, Long targetId) {
        return userRepository
                .findByIdAndCompanyAdminIdAndDeletedAtIsNull(targetId, PackageService.resolveOwnerId(actor))
                .orElseThrow(() -> new ApiException("User not found.", HttpStatus.NOT_FOUND));
    }

    private static void rejectSelf(User actor, User target, String message) {
        if (actor.getId().equals(target.getId())) {
            throw new ApiException(message, HttpStatus.BAD_REQUEST);
        }
    }

    private static void clearSuspension(User user) {
        user.setSuspendedFrom(null);
        user.setSuspendedTo(null);
    }

    private static void forceOffline(User user) {
        user.setOnline(false);
    }

    /**
     * Keeps the row (payments, certificates, progress still point at it) but disables it and frees
     * the email so the same address can be invited again. Name/phone stay for those records.
     */
    private void softDelete(User user) {
        user.setActive(false);
        user.setOnline(false);
        clearSuspension(user);
        user.setDeletedAt(LocalDateTime.now());
        user.setEmail("deleted-" + user.getId() + "@deleted.invalid");
        user.setTwoFaEnabled(false);
        user.setTwoFaMethod(null);
        user.setTwoFaSecret(null);
        userRepository.save(user);
    }

    private static String normaliseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "active" -> ACTIVE;
            case "inactive" -> INACTIVE;
            case "suspended" -> SUSPENDED;
            default -> throw new ApiException("Status must be Active, Inactive or Suspended.", HttpStatus.BAD_REQUEST);
        };
    }

    static String statusOf(User user, LocalDate today) {
        if (!Boolean.TRUE.equals(user.getActive())) {
            return INACTIVE;
        }
        return user.isSuspendedOn(today) ? SUSPENDED : ACTIVE;
    }

    private static UserManagementDto.Row toRow(User u, LocalDate today) {
        return UserManagementDto.Row.builder()
                .id(u.getId())
                .username((u.getFirstName() + " " + u.getLastName()).trim())
                .role(u.getRole())
                .email(u.getEmail())
                .status(statusOf(u, today))
                .department(u.getDepartment())
                .suspendFrom(u.getSuspendedFrom())
                .suspendTo(u.getSuspendedTo())
                .build();
    }
}