package com.example.lms.service;

import com.example.lms.dto.ProfileDto;
import com.example.lms.entity.OrganizationDetails;
import com.example.lms.entity.PersonalDetails;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.OrganizationDetailsRepository;
import com.example.lms.repository.PersonalDetailsRepository;
import com.example.lms.repository.UserRepository;
import com.example.lms.util.PhoneValidationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneId;
import java.util.Locale;

/**
 * Where each piece of data lives:
 *  - users                : first/last name, email, country code, phone (signup data)
 *  - organization_details : organization name + organization address (ADMIN / SUPER_ADMIN / INSTRUCTOR)
 *  - personal_details     : personal address, time zone, country, state, city, postal code, pictures
 * The two detail tables are linked to users through a unique user_id foreign key and are
 * created lazily the first time they are needed.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final OrganizationDetailsRepository organizationRepository;
    private final PersonalDetailsRepository personalRepository;
    private final ProfileImageStorageService imageStorage;

    /* ------------------------------------------------------------------ */
    /*  Read                                                              */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public ProfileDto.Response getMyProfile(Long userId) {
        User user = loadUser(userId);
        return toResponse(user,
                organizationRepository.findByUserId(userId).orElse(null),
                personalRepository.findByUserId(userId).orElse(null));
    }

    /* ------------------------------------------------------------------ */
    /*  Update                                                            */
    /* ------------------------------------------------------------------ */

    @Transactional
    public ProfileDto.Response updateMyProfile(Long userId, ProfileDto.UpdateRequest req) {
        User user = loadUser(userId);
        boolean organizationOwner = canEditOrganization(user);

        // ---- Validate everything first ----
        String timeZone = cleanLine(req.getTimeZone());
        if (timeZone != null && !ZoneId.getAvailableZoneIds().contains(timeZone)) {
            throw new ApiException("Select a valid time zone.", HttpStatus.BAD_REQUEST);
        }

        // Same rules as signup, for every role: the country code is the user's own choice.
        String countryCode = notBlank(req.getCountryCode()) ? req.getCountryCode().trim() : user.getCountryCode();
        if (!PhoneValidationUtil.isSupportedCountryCode(countryCode)) {
            throw new ApiException("Country Code is required", HttpStatus.BAD_REQUEST);
        }
        String phone = req.getPhoneNumber().trim();
        if (!PhoneValidationUtil.isValid(countryCode, phone)) {
            throw new ApiException(
                    "Enter a phone number with a valid length for the selected country code.",
                    HttpStatus.BAD_REQUEST);
        }

        String newEmail = req.getEmail().toLowerCase(Locale.ROOT);
        if (!newEmail.equalsIgnoreCase(user.getEmail())) {
            boolean taken = userRepository.findByEmailIgnoreCase(newEmail)
                    .filter(other -> !other.getId().equals(user.getId()))
                    .isPresent();
            if (taken) {
                throw new ApiException("Email ID already exists.", HttpStatus.CONFLICT);
            }
            user.setEmail(newEmail);
        }

        // ---- users table (signup data) ----
        user.setFirstName(req.getFirstName().trim());
        user.setLastName(req.getLastName().trim());
        user.setCountryCode(countryCode);
        user.setPhoneNumber(phone);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race on the unique email index.
            throw new ApiException("Email ID already exists.", HttpStatus.CONFLICT);
        }

        // ---- personal_details (every user) ----
        PersonalDetails personal = personalFor(user);
        personal.setPersonalAddress(cleanMultiline(req.getPersonalAddress()));
        personal.setTimeZone(timeZone);
        personal.setCountry(cleanLine(req.getCountry()));
        personal.setState(cleanLine(req.getState()));
        personal.setCity(cleanLine(req.getCity()));
        personal.setPostalCode(cleanLine(req.getPostalCode()));
        personal = personalRepository.save(personal);

        // ---- organization_details (admin / super admin / instructor) ----
        OrganizationDetails organization = organizationRepository.findByUserId(userId).orElse(null);
        if (organizationOwner) {
            if (organization == null) {
                organization = OrganizationDetails.builder().user(user).build();
            }
            // Organization Name: text, numbers and special characters; max 500 (enforced by @Size too).
            organization.setOrganizationName(cleanLine(req.getOrganizationName()));
            organization.setOrganizationAddress(cleanMultiline(req.getOrganizationAddress()));
            organization = organizationRepository.save(organization);
        }

        return toResponse(user, organization, personal);
    }

    /* ------------------------------------------------------------------ */
    /*  Pictures (stored in personal_details)                             */
    /* ------------------------------------------------------------------ */

    @Transactional
    public ProfileDto.Response updateProfilePicture(Long userId, MultipartFile file) {
        User user = loadUser(userId);
        PersonalDetails personal = personalFor(user);
        String oldUrl = personal.getProfilePictureUrl();
        personal.setProfilePictureUrl(imageStorage.store(file, "avatar"));
        personal = personalRepository.saveAndFlush(personal);
        imageStorage.delete(oldUrl);
        return toResponse(user, organizationOf(userId), personal);
    }

    @Transactional
    public ProfileDto.Response updateBackgroundPicture(Long userId, MultipartFile file) {
        User user = loadUser(userId);
        PersonalDetails personal = personalFor(user);
        String oldUrl = personal.getBackgroundPictureUrl();
        personal.setBackgroundPictureUrl(imageStorage.store(file, "background"));
        personal = personalRepository.saveAndFlush(personal);
        imageStorage.delete(oldUrl);
        return toResponse(user, organizationOf(userId), personal);
    }

    @Transactional
    public ProfileDto.Response removeProfilePicture(Long userId) {
        User user = loadUser(userId);
        PersonalDetails personal = personalFor(user);
        String oldUrl = personal.getProfilePictureUrl();
        personal.setProfilePictureUrl(null);
        personal = personalRepository.saveAndFlush(personal);
        imageStorage.delete(oldUrl);
        return toResponse(user, organizationOf(userId), personal);
    }

    @Transactional
    public ProfileDto.Response removeBackgroundPicture(Long userId) {
        User user = loadUser(userId);
        PersonalDetails personal = personalFor(user);
        String oldUrl = personal.getBackgroundPictureUrl();
        personal.setBackgroundPictureUrl(null);
        personal = personalRepository.saveAndFlush(personal);
        imageStorage.delete(oldUrl);
        return toResponse(user, organizationOf(userId), personal);
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found.", HttpStatus.NOT_FOUND));
    }

    /** Existing row, or a new (not yet saved) one linked to this user. */
    private PersonalDetails personalFor(User user) {
        return personalRepository.findByUserId(user.getId())
                .orElseGet(() -> PersonalDetails.builder().user(user).build());
    }

    private OrganizationDetails organizationOf(Long userId) {
        return organizationRepository.findByUserId(userId).orElse(null);
    }

    /** Admin, super admin and instructor may view/update their organization details. */
    private static boolean canEditOrganization(User user) {
        String role = user.getRole();
        return "ADMIN".equalsIgnoreCase(role)
                || "SUPER_ADMIN".equalsIgnoreCase(role)
                || "INSTRUCTOR".equalsIgnoreCase(role);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Single-line text: control characters (incl. newlines) become spaces; blank -> null. */
    private static String cleanLine(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("\\p{Cntrl}", " ").trim().replaceAll(" {2,}", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** Multi-line text: keeps \n, removes other control characters; blank -> null. */
    private static String cleanMultiline(String value) {
        if (value == null) return null;
        String cleaned = value.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\\n]]", "")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** organization / personal may be null (no row yet) - the response then just has null fields. */
    private ProfileDto.Response toResponse(User u, OrganizationDetails org, PersonalDetails p) {
        return ProfileDto.Response.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .role(u.getRole())
                .active(Boolean.TRUE.equals(u.getActive()))
                .countryCode(u.getCountryCode())
                .phoneNumber(u.getPhoneNumber())
                .organizationName(org == null ? null : org.getOrganizationName())
                .organizationAddress(org == null ? null : org.getOrganizationAddress())
                .personalAddress(p == null ? null : p.getPersonalAddress())
                .timeZone(p == null ? null : p.getTimeZone())
                .country(p == null ? null : p.getCountry())
                .state(p == null ? null : p.getState())
                .city(p == null ? null : p.getCity())
                .postalCode(p == null ? null : p.getPostalCode())
                .profilePictureUrl(p == null ? null : p.getProfilePictureUrl())
                .backgroundPictureUrl(p == null ? null : p.getBackgroundPictureUrl())
                .build();
    }
}