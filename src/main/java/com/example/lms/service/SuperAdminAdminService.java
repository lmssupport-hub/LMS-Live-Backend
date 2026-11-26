package com.example.lms.service;

import com.example.lms.dto.ProfileDto;
import com.example.lms.dto.SuperAdminAdminDto;
import com.example.lms.dto.UserManagementDto;
import com.example.lms.entity.OrganizationDetails;
import com.example.lms.entity.PersonalDetails;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.OrganizationDetailsRepository;
import com.example.lms.repository.PersonalDetailsRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Super Admin > Admins: list / status / delete / edit-profile of the company ADMIN accounts.
 *
 * Only a company's own ADMIN can be a target (404 for anyone else), so these routes can never
 * touch a SUPER_ADMIN or a company's team members. Status and delete rules are the ones of the
 * All Users page ({@link UserManagementService}).
 */
@Service
@RequiredArgsConstructor
public class SuperAdminAdminService {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminAdminService.class);

    private static final String ADMIN_ROLE = "ADMIN";
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final OrganizationDetailsRepository organizationRepository;
    private final PersonalDetailsRepository personalRepository;
    private final ProfileService profileService;

    @Transactional(readOnly = true)
    public SuperAdminAdminDto.PageResponse list(int page, int size) {
        Page<User> result = userRepository.findByRoleAndCompanyAdminIdIsNullAndDeletedAtIsNull(
                ADMIN_ROLE,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));

        return SuperAdminAdminDto.PageResponse.builder()
                .content(toRows(result.getContent()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional
    public SuperAdminAdminDto.Row updateStatus(Long actorId, Long adminId, UserManagementDto.StatusRequest req) {
        User target = findAdmin(adminId);
        UserManagementService.applyStatus(target, req, LocalDate.now());
        userRepository.save(target);
        log.info("Admin {} set to {} by super admin {}", target.getId(), req.getStatus(), actorId);
        return toRows(List.of(target)).get(0);
    }

    /** Soft delete: the row stays for payments / revenue history, the account cannot log in again. */
    @Transactional
    public void delete(Long actorId, Long adminId) {
        User target = findAdmin(adminId);
        UserManagementService.markDeleted(target);
        userRepository.save(target);
        log.info("Admin {} deleted by super admin {}", target.getId(), actorId);
    }

    /* ---- Edit Profile of an admin (reuses ProfileService) ---- */

    @Transactional(readOnly = true)
    public ProfileDto.Response getProfile(Long adminId) {
        return profileService.getMyProfile(findAdmin(adminId).getId());
    }

    @Transactional
    public ProfileDto.Response updateProfile(Long adminId, ProfileDto.UpdateRequest req) {
        return profileService.updateProfile(findAdmin(adminId).getId(), req, true);
    }

    @Transactional
    public ProfileDto.Response uploadProfilePicture(Long adminId, MultipartFile file) {
        return profileService.updateProfilePicture(findAdmin(adminId).getId(), file);
    }

    @Transactional
    public ProfileDto.Response uploadBackgroundPicture(Long adminId, MultipartFile file) {
        return profileService.updateBackgroundPicture(findAdmin(adminId).getId(), file);
    }

    @Transactional
    public ProfileDto.Response removeProfilePicture(Long adminId) {
        return profileService.removeProfilePicture(findAdmin(adminId).getId());
    }

    @Transactional
    public ProfileDto.Response removeBackgroundPicture(Long adminId) {
        return profileService.removeBackgroundPicture(findAdmin(adminId).getId());
    }

    /* ---- helpers ---- */

    private User findAdmin(Long adminId) {
        return userRepository.findByIdAndRoleAndCompanyAdminIdIsNullAndDeletedAtIsNull(adminId, ADMIN_ROLE)
                .orElseThrow(() -> new ApiException("Admin not found.", HttpStatus.NOT_FOUND));
    }

    private List<SuperAdminAdminDto.Row> toRows(List<User> admins) {
        List<Long> ids = admins.stream().map(User::getId).toList();

        Map<Long, String> organizations = new HashMap<>();
        for (OrganizationDetails o : organizationRepository.findByUserIdIn(ids)) {
            organizations.put(o.getUser().getId(), o.getOrganizationName());
        }
        Map<Long, String> pictures = new HashMap<>();
        for (PersonalDetails p : personalRepository.findByUserIdIn(ids)) {
            pictures.put(p.getUser().getId(), p.getProfilePictureUrl());
        }

        LocalDate today = LocalDate.now();
        return admins.stream()
                .map(u -> SuperAdminAdminDto.Row.builder()
                        .id(u.getId())
                        .username((u.getFirstName() + " " + u.getLastName()).trim())
                        .email(u.getEmail())
                        .organization(organizations.get(u.getId()))
                        .profilePictureUrl(pictures.get(u.getId()))
                        .status(UserManagementService.statusOf(u, today))
                        .suspendFrom(u.getSuspendedFrom())
                        .suspendTo(u.getSuspendedTo())
                        .build())
                .toList();
    }
}