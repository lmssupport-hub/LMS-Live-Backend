package com.example.lms.service;

import com.example.lms.dto.LoginDto;
import com.example.lms.dto.PermissionDto;
import com.example.lms.dto.TwoFactorDto;
import com.example.lms.entity.LoginEventEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.LoginEventRepository;
import com.example.lms.repository.UserRepository;
import com.example.lms.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LoginService {

    public static final String NO_PACKAGE_MESSAGE =
            "No package is assigned to your account. Please contact your administrator.";
    public static final String LOGIN_ACCESS_DENIED_MESSAGE =
            "Login access is not available for your package. Please contact your administrator.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final PackageService packageService;
    private final PermissionService permissionService;
    private final LoginEventRepository loginEventRepository;
    private final TwoFactorService twoFactorService;

    @Value("${app.login.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.login.lockout-minutes:15}")
    private int lockoutMinutes;

    // noRollbackFor: without it, the failed-attempt counter saved in
    // registerFailedAttempt() is rolled back when ApiException is thrown,
    // so account lockout would never persist.
    @Transactional(noRollbackFor = ApiException.class)
    public LoginDto.LoginResponse login(LoginDto.LoginRequest request) {
        try {
            User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                    .orElseThrow(() -> new ApiException("Invalid Email ID or Password.", HttpStatus.UNAUTHORIZED));

            if (user.isLocked()) {
                throw new ApiException(
                        "Too many failed login attempts. Please try again after " + lockoutMinutes + " minutes.",
                        HttpStatus.LOCKED);
            }

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                registerFailedAttempt(user);
                throw new ApiException("Invalid Email ID or Password.", HttpStatus.UNAUTHORIZED);
            }

            assertAccountUsable(user);

            resetFailedAttempts(user);

            boolean rememberMe = Boolean.TRUE.equals(request.getRememberMe());

            // 2FA on: do NOT issue a JWT yet. E-mail an OTP and return only an opaque challenge token.
            if (user.isTwoFaActive()) {
                TwoFactorService.IssuedChallenge challenge = twoFactorService.issueLoginChallenge(user, rememberMe);
                return LoginDto.LoginResponse.builder()
                        .twoFactorRequired(true)
                        .challengeToken(challenge.challengeToken())
                        .maskedEmail(challenge.maskedEmail())
                        .twoFactorMethod(challenge.method())
                        .build();
            }

            return completeLogin(user, rememberMe);

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Unable to log in. Please try again later.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** POST /api/auth/2fa/login/verify - second step of login when 2FA is on. */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginDto.LoginResponse verifyTwoFactorLogin(TwoFactorDto.LoginVerifyRequest request) {
        try {
            TwoFactorService.VerifiedLogin verified =
                    twoFactorService.verifyLoginChallenge(request.getChallengeToken(), request.getOtp());

            User user = userRepository.findById(verified.userId())
                    .orElseThrow(() -> new ApiException(
                            "Your login session has expired. Please log in again.", HttpStatus.GONE));

            // State may have changed since the password step (deactivated, package removed...).
            assertAccountUsable(user);
            return completeLogin(user, verified.rememberMe());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Unable to log in. Please try again later.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** POST /api/auth/2fa/login/resend */
    public void resendTwoFactorLoginOtp(TwoFactorDto.LoginResendRequest request) {
        twoFactorService.resendLoginChallenge(request.getChallengeToken());
    }

    /** Active user + (for package-gated admins) a package that still allows login. */
    private void assertAccountUsable(User user) {
        if (!Boolean.TRUE.equals(user.getActive()) || user.isDeleted()) {
            throw new ApiException("Your account is inactive. Please contact support.", HttpStatus.FORBIDDEN);
        }
        if (user.isSuspendedOn(java.time.LocalDate.now())) {
            throw new ApiException(
                    "Your account is suspended until " + user.getSuspendedTo() + ". Please contact your administrator.",
                    HttpStatus.FORBIDDEN);
        }

        // Package gate: runs only after the password is verified, so it
        // never leaks package state to someone who doesn't know the password.
        if (PackageService.isPackageGatedAdmin(user)) {
            if (user.getPackageId() == null) {
                throw new ApiException(NO_PACKAGE_MESSAGE, HttpStatus.FORBIDDEN);
            }
            if (!packageService.hasLoginAccess(user.getPackageId())) {
                throw new ApiException(LOGIN_ACCESS_DENIED_MESSAGE, HttpStatus.FORBIDDEN);
            }
        }
    }

    /** Issues the JWT + permission snapshot. Reached only after the password (and OTP, if 2FA is on) passed. */
    private LoginDto.LoginResponse completeLogin(User user, boolean rememberMe) {
        recordLoginEvent(user);

        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getRole(), rememberMe);
        PermissionDto.Snapshot access = permissionService.snapshotOf(user);

        return LoginDto.LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .fullAccess(access.isFullAccess())
                .companyAdmin(access.isCompanyAdmin())
                .permissions(access.getPermissions())
                .menus(access.getMenus())
                .build();
    }

    /** Dashboard statistics only - a failure here must never block a valid login. */
    private void recordLoginEvent(User user) {
        try {
            loginEventRepository.save(new LoginEventEntity(user.getId(), PackageService.resolveOwnerId(user)));
            user.setOnline(true);
            user.setLastSeenAt(LocalDateTime.now());
            userRepository.save(user);
        } catch (RuntimeException ignored) {
            // statistics are best-effort
        }
    }

    /** POST /api/auth/heartbeat - the browser is still open; counted as online for the next 2 minutes. */
    @Transactional
    public void heartbeat(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        // Time on platform: the gap since the previous heartbeat counts only if the browser stayed open
        // (heartbeats come every ~45 s; a gap over 2 minutes = it was closed, so nothing is added).
        userRepository.findLastSeenAtIfOnline(userId).ifPresent(previous -> {
            long gap = java.time.Duration.between(previous, now).getSeconds();
            if (gap > 0 && gap <= 120) {
                userRepository.addPlatformSeconds(userId, Math.min(gap, 90));
            }
        });
        userRepository.touchLastSeen(userId, now);
    }

    /** POST /api/auth/logout - the user is no longer "online" on the dashboard. */
    @Transactional
    public void logout(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setOnline(false);
            userRepository.save(user);
        });
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() == null ? 1 : user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxFailedAttempts) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
        }
        userRepository.save(user);
    }

    private void resetFailedAttempts(User user) {
        if ((user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0)
                || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }
}