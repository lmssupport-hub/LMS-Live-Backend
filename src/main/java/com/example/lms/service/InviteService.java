package com.example.lms.service;

import com.example.lms.dto.InviteDto;
import com.example.lms.entity.InviteEntity;
import com.example.lms.entity.RoleEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.InviteRepository;
import com.example.lms.repository.RoleRepository;
import com.example.lms.repository.UserRepository;
import com.example.lms.util.PhoneValidationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class InviteService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InviteRepository inviteRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Reuses the SAME email port your forgot-password flow already uses
    // (ResendEmailService implements this) - see sendInviteEmail(...) added
    // to that interface/impl.
    private final PasswordResetEmailService emailService;

    @Value("${app.invite.expiry-minutes:4320}") // 72h default
    private int expiryMinutes;

    // Same property your forgot-password reset link build should already be
    // reading to know the Angular app's origin - reuse that key if it exists
    // under a different name.
    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Transactional
    public InviteDto.SendResponse sendInvite(Long adminId, InviteDto.SendRequest request) {
        RoleEntity role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ApiException("Selected role does not exist.", HttpStatus.NOT_FOUND));

        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException("This email is already registered.", HttpStatus.CONFLICT);
        }
        if (inviteRepository.existsByEmailIgnoreCaseAndStatus(email, "PENDING")) {
            throw new ApiException("An invite is already pending for this email.", HttpStatus.CONFLICT);
        }

        InviteEntity invite = new InviteEntity();
        invite.setEmail(email);
        invite.setRoleId(role.getId());
        invite.setRoleName(role.getName());
        invite.setInvitedByAdminId(adminId);
        invite.setToken(generateToken());
        invite.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));

        try {
            invite = inviteRepository.save(invite);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException("An invite is already pending for this email.", HttpStatus.CONFLICT);
        }

        String signupLink = frontendBaseUrl + "/auth?invite=" + invite.getToken();
        emailService.sendInviteEmail(invite.getEmail(), signupLink, role.getName(), expiryMinutes);

        return InviteDto.SendResponse.builder()
                .email(invite.getEmail())
                .roleName(role.getName())
                .expiresAt(invite.getExpiresAt())
                .build();
    }

    public InviteDto.InviteDetails getInviteDetails(String token) {
        InviteEntity invite = requireValidPendingInvite(token);
        return InviteDto.InviteDetails.builder()
                .email(invite.getEmail())
                .roleName(invite.getRoleName())
                .expiresAt(invite.getExpiresAt())
                .build();
    }

    @Transactional
    public void acceptInvite(String token, InviteDto.AcceptRequest request) {
        InviteEntity invite = requireValidPendingInvite(token);

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new ApiException("Passwords do not match.", HttpStatus.BAD_REQUEST);
        }
        if (request.getPassword().equalsIgnoreCase(invite.getEmail())) {
            throw new ApiException("Password should not match Email ID.", HttpStatus.BAD_REQUEST);
        }
        if (!PhoneValidationUtil.isSupportedCountryCode(request.getCountryCode())) {
            throw new ApiException("Country Code is required", HttpStatus.BAD_REQUEST);
        }
        if (!PhoneValidationUtil.isValid(request.getCountryCode(), request.getPhoneNumber())) {
            throw new ApiException(
                    "Enter a phone number with a valid length for the selected country code.",
                    HttpStatus.BAD_REQUEST);
        }
        if (!Boolean.TRUE.equals(request.getAcceptTerms())) {
            throw new ApiException("Please accept the Terms & Conditions.", HttpStatus.BAD_REQUEST);
        }
        if (userRepository.existsByEmailIgnoreCase(invite.getEmail())) {
            throw new ApiException("This email is already registered.", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(invite.getEmail())
                .countryCode(request.getCountryCode())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(invite.getRoleName())
                .roleId(invite.getRoleId())
                .companyAdminId(invite.getInvitedByAdminId())
                .acceptedTerms(request.getAcceptTerms())
                .active(true)
                .build();

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException("This email is already registered.", HttpStatus.CONFLICT);
        }

        invite.setStatus("ACCEPTED");
        invite.setAcceptedAt(LocalDateTime.now());
        inviteRepository.save(invite);
    }

    private InviteEntity requireValidPendingInvite(String token) {
        InviteEntity invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new ApiException("This invite link is invalid.", HttpStatus.NOT_FOUND));

        if (!"PENDING".equals(invite.getStatus())) {
            throw new ApiException("This invite link has already been used.", HttpStatus.GONE);
        }
        if (invite.isExpired()) {
            invite.setStatus("EXPIRED");
            inviteRepository.save(invite);
            throw new ApiException("This invite link has expired. Please ask your admin to resend it.", HttpStatus.GONE);
        }
        return invite;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}