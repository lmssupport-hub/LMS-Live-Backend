package com.example.lms.service;

import com.example.lms.dto.ChangePasswordDto;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.UserRepository;
import com.example.lms.util.CommonPasswordChecker;
import com.example.lms.util.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangePasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void changePassword(Long userId, ChangePasswordDto.ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found.", HttpStatus.NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new ApiException("Your account is inactive. Please contact support.", HttpStatus.FORBIDDEN);
        }
        // 400, NOT 401: frontend interceptor 401 vandha logout pannidum
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new ApiException("Current Password is incorrect.", HttpStatus.BAD_REQUEST);
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ApiException("Passwords do not match.", HttpStatus.BAD_REQUEST);
        }
        if (req.getNewPassword().equals(req.getCurrentPassword())) {
            throw new ApiException("Choose a password different from your current one.", HttpStatus.BAD_REQUEST);
        }
        if (PasswordPolicy.hasEmoji(req.getNewPassword())) {
            throw new ApiException("Emojis are not allowed in passwords.", HttpStatus.BAD_REQUEST);
        }
        if (req.getNewPassword().equalsIgnoreCase(user.getEmail())) {
            throw new ApiException("Password should not match Email ID.", HttpStatus.BAD_REQUEST);
        }
        if (CommonPasswordChecker.isCommon(req.getNewPassword())) {
            throw new ApiException("Password should not be a commonly used password.", HttpStatus.BAD_REQUEST);
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }
}