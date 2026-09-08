package com.example.lms.service;
import com.example.lms.dto.SignUpDto;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.UserRepository;
import com.example.lms.util.CommonPasswordChecker;
import com.example.lms.util.PhoneValidationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignUpService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignUpDto.SignUpResponse signUp(SignUpDto.SignUpRequest request) {
        try {
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                throw new ApiException("Passwords do not match.", HttpStatus.BAD_REQUEST);
            }
            if (request.getPassword().equalsIgnoreCase(request.getEmail())) {
                throw new ApiException("Password should not match Email ID.", HttpStatus.BAD_REQUEST);
            }
            if (CommonPasswordChecker.isCommon(request.getPassword())) {
                throw new ApiException("Password should not be a commonly used password.", HttpStatus.BAD_REQUEST);
            }
            if (!PhoneValidationUtil.isSupportedCountryCode(request.getCountryCode())) {
                throw new ApiException("Country Code is required", HttpStatus.BAD_REQUEST);
            }
            if (!PhoneValidationUtil.isValid(request.getCountryCode(), request.getPhoneNumber())) {
                throw new ApiException(
                        "Enter a phone number with a valid length for the selected country code.",
                        HttpStatus.BAD_REQUEST);
            }
            if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
                throw new ApiException("Email ID already exists.", HttpStatus.CONFLICT);
            }
            User user = User.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .countryCode(request.getCountryCode())
                    .phoneNumber(request.getPhoneNumber())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role("ADMIN")
                    .acceptedTerms(request.getAcceptTerms())
                    .active(true)
                    .build();
            User saved;
            try {
                saved = userRepository.save(user);
            } catch (DataIntegrityViolationException e) {
                throw new ApiException("Email ID already exists.", HttpStatus.CONFLICT);
            }
            return SignUpDto.SignUpResponse.builder()
                    .id(saved.getId())
                    .firstName(saved.getFirstName())
                    .lastName(saved.getLastName())
                    .email(saved.getEmail())
                    .countryCode(saved.getCountryCode())
                    .phoneNumber(saved.getPhoneNumber())
                    .role(saved.getRole())
                    .build();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Unable to create an account. Please try again later.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Backs GET /api/auth/signup/admins — User-table fields only, no package join. */
    public Page<SignUpDto.AdminSummary> listAdmins(String search, String status, Pageable pageable) {
        Boolean active = switch (status == null ? "All" : status) {
            case "Active" -> Boolean.TRUE;
            case "Inactive" -> Boolean.FALSE;
            default -> null;
        };
        return userRepository.findAdmins(search, active, pageable).map(this::toAdminSummary);
    }

    private SignUpDto.AdminSummary toAdminSummary(User u) {
        return SignUpDto.AdminSummary.builder()
                .id(u.getId())
                .username((u.getFirstName() + " " + u.getLastName()).trim())
                .email(u.getEmail())
                .role(u.getRole())
                .status(Boolean.TRUE.equals(u.getActive()) ? "Active" : "Inactive")
                .packageId(u.getPackageId())
                .packageExpiresAt(u.getPackageExpiresAt())
                .createdAt(u.getCreatedAt())
                .build();
    }
}