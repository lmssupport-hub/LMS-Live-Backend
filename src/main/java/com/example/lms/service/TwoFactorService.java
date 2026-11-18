package com.example.lms.service;

import com.example.lms.dto.TwoFactorDto;
import com.example.lms.entity.TwoFactorChallenge;
import com.example.lms.entity.TwoFactorChallenge.Purpose;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.TwoFactorChallengeRepository;
import com.example.lms.repository.UserRepository;
import com.example.lms.util.SecretCipher;
import com.example.lms.util.TokenGenerator;
import com.example.lms.util.TotpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Two-factor authentication: e-mail OTP and Google Authenticator (TOTP).
 *
 * Two flows share the same challenge machinery:
 *  1) LOGIN  - password was correct, 2FA is on: create a challenge, hand the browser an opaque
 *              challengeToken, and issue the JWT only after the code is verified.
 *              EMAIL         -> a 6-digit OTP is mailed.
 *              AUTHENTICATOR -> nothing is sent; the user types the code from the app.
 *  2) SETTINGS (ENABLE / DISABLE) - a logged-in user turns 2FA on / off. Both need a fresh code so a
 *              stolen session alone cannot switch 2FA off. Enabling the authenticator first shows a
 *              QR code (startAuthenticatorSetup) and only activates once a code from the app is entered.
 *
 * Rules: e-mail OTPs are stored only as a BCrypt hash, expire after {@code otp-expiry-minutes}, are single
 * use and cannot be re-requested within {@code resend-cooldown-seconds}. TOTP secrets are stored AES-GCM
 * encrypted; a TOTP step can be used once. Both allow {@code max-attempts} wrong tries per challenge.
 *
 * Every @Transactional method uses noRollbackFor = ApiException so the wrong-attempt counter survives
 * the exception. The e-mail is sent BEFORE any entity is modified, so a failed send never leaves a
 * half-updated challenge behind.
 */
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    public static final String METHOD_EMAIL = "EMAIL";
    public static final String METHOD_AUTHENTICATOR = "AUTHENTICATOR";

    /** otp_hash is NOT NULL; authenticator challenges have no mailed OTP, so they carry this placeholder. */
    private static final String NO_OTP = "N/A";

    public record IssuedChallenge(String challengeToken, String maskedEmail, String method) {}

    public record VerifiedLogin(Long userId, boolean rememberMe) {}

    private static final String ACTION_LOGIN = "log in to your account";
    private static final String ACTION_ENABLE = "turn on 2-step verification";
    private static final String ACTION_DISABLE = "turn off 2-step verification";

    private static final String SESSION_EXPIRED = "Your login session has expired. Please log in again.";

    private final UserRepository userRepository;
    private final TwoFactorChallengeRepository challengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetEmailService emailService;
    private final SecretCipher secretCipher;

    @Value("${app.two-factor.otp-expiry-minutes:10}")
    private int otpExpiryMinutes;

    @Value("${app.two-factor.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.two-factor.resend-cooldown-seconds:30}")
    private int resendCooldownSeconds;

    /** Shown inside Google Authenticator next to the account name. */
    @Value("${app.two-factor.issuer:Vativa Hub LMS}")
    private String issuer;

    /* ====================================================================== */
    /*  Login flow                                                            */
    /* ====================================================================== */

    /** Called by LoginService after the password (and account / package gates) passed. */
    @Transactional(noRollbackFor = ApiException.class)
    public IssuedChallenge issueLoginChallenge(User user, boolean rememberMe) {
        boolean authenticator = METHOD_AUTHENTICATOR.equals(user.getTwoFaMethod());

        TwoFactorChallenge challenge = challengeRepository
                .findByUserIdAndPurposeForUpdate(user.getId(), Purpose.LOGIN)
                .orElseGet(() -> TwoFactorChallenge.builder()
                        .userId(user.getId())
                        .purpose(Purpose.LOGIN)
                        .build());
        // Also caps TOTP guessing: a fresh challenge (= 5 new attempts) is at most one per cooldown.
        enforceCooldown(challenge);

        String rawToken = TokenGenerator.generateRawToken();
        if (authenticator) {
            startAuthenticatorChallenge(challenge, null);
        } else {
            sendAndStore(challenge, user, ACTION_LOGIN);
        }
        challenge.setTokenHash(TokenGenerator.hash(rawToken));
        challenge.setRememberMe(rememberMe);
        challengeRepository.save(challenge);

        return new IssuedChallenge(rawToken,
                authenticator ? "" : maskEmail(user.getEmail()),
                authenticator ? METHOD_AUTHENTICATOR : METHOD_EMAIL);
    }

    /** "Resend OTP" on the login screen (e-mail only). The challengeToken stays the same. */
    @Transactional(noRollbackFor = ApiException.class)
    public void resendLoginChallenge(String challengeToken) {
        TwoFactorChallenge challenge = findLoginChallenge(challengeToken);
        if (METHOD_AUTHENTICATOR.equals(challenge.getMethod())) {
            throw new ApiException("Codes from Google Authenticator cannot be re-sent. Open the app and enter the current code.",
                    HttpStatus.BAD_REQUEST);
        }
        if (challenge.isExpired()) {
            challengeRepository.delete(challenge);
            throw new ApiException(SESSION_EXPIRED, HttpStatus.GONE);
        }
        enforceCooldown(challenge);

        User user = userRepository.findById(challenge.getUserId())
                .orElseThrow(() -> new ApiException(SESSION_EXPIRED, HttpStatus.GONE));
        sendAndStore(challenge, user, ACTION_LOGIN);
        challengeRepository.save(challenge);
    }

    /**
     * Checks the code and consumes the challenge. The caller (LoginService) then issues the JWT.
     * Always run inside the caller's transaction so the row lock is held until the challenge is gone.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public VerifiedLogin verifyLoginChallenge(String challengeToken, String otp) {
        TwoFactorChallenge challenge = findLoginChallenge(challengeToken);
        User user = userRepository.findById(challenge.getUserId())
                .orElseThrow(() -> new ApiException(SESSION_EXPIRED, HttpStatus.GONE));

        boolean authenticator = METHOD_AUTHENTICATOR.equals(challenge.getMethod());
        String secret = authenticator ? secretCipher.decrypt(user.getTwoFaSecret()) : null;
        long step = checkCode(challenge, otp, secret, lastStep(user));

        if (authenticator) {
            user.setTwoFaLastStep(step);
            userRepository.save(user);
        }
        challengeRepository.delete(challenge);
        return new VerifiedLogin(challenge.getUserId(), Boolean.TRUE.equals(challenge.getRememberMe()));
    }

    /* ====================================================================== */
    /*  Settings flow (user is already logged in)                             */
    /* ====================================================================== */

    @Transactional(readOnly = true)
    public TwoFactorDto.Status status(Long userId) {
        User user = loadUser(userId);
        return TwoFactorDto.Status.builder()
                .enabled(user.isTwoFaActive())
                .method(user.isTwoFaActive() ? user.getTwoFaMethod() : null)
                .maskedEmail(maskEmail(user.getEmail()))
                .build();
    }

    /**
     * ENABLE with e-mail: mails the OTP (first "Next" click and every "Resend OTP").
     * DISABLE with e-mail: mails the OTP.
     * DISABLE with authenticator: nothing is mailed - just opens a challenge that counts wrong attempts.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void sendSettingsOtp(Long userId, Purpose purpose) {
        if (purpose != Purpose.ENABLE && purpose != Purpose.DISABLE) {
            throw new ApiException("Invalid request.", HttpStatus.BAD_REQUEST);
        }
        User user = loadUser(userId);
        assertStateAllows(user, purpose);

        TwoFactorChallenge challenge = findOrNewSettingsChallenge(userId, purpose);

        if (purpose == Purpose.DISABLE && METHOD_AUTHENTICATOR.equals(user.getTwoFaMethod())) {
            startAuthenticatorChallenge(challenge, null);
        } else {
            enforceCooldown(challenge);
            sendAndStore(challenge, user, purpose == Purpose.ENABLE ? ACTION_ENABLE : ACTION_DISABLE);
        }
        challengeRepository.save(challenge);
    }

    /** ENABLE with Google Authenticator, step 1: new secret + QR payload. Not active until confirmEnable(). */
    @Transactional(noRollbackFor = ApiException.class)
    public TwoFactorDto.AuthenticatorSetup startAuthenticatorSetup(Long userId) {
        User user = loadUser(userId);
        assertStateAllows(user, Purpose.ENABLE);

        String secret = TotpUtil.generateSecret();
        TwoFactorChallenge challenge = findOrNewSettingsChallenge(userId, Purpose.ENABLE);
        startAuthenticatorChallenge(challenge, secretCipher.encrypt(secret));
        challengeRepository.save(challenge);

        return TwoFactorDto.AuthenticatorSetup.builder()
                .secret(secret)
                .otpauthUri(TotpUtil.otpauthUri(issuer, user.getEmail(), secret))
                .build();
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void confirmEnable(Long userId, String otp) {
        User user = loadUser(userId);
        assertStateAllows(user, Purpose.ENABLE);

        TwoFactorChallenge challenge = findSettingsChallenge(userId, Purpose.ENABLE);
        boolean authenticator = METHOD_AUTHENTICATOR.equals(challenge.getMethod());

        if (authenticator && challenge.getPendingSecret() == null) {
            throw new ApiException("Please scan the QR code first.", HttpStatus.BAD_REQUEST);
        }
        String secret = authenticator ? secretCipher.decrypt(challenge.getPendingSecret()) : null;
        long step = checkCode(challenge, otp, secret, -1);
        challengeRepository.delete(challenge);

        user.setTwoFaEnabled(true);
        if (authenticator) {
            user.setTwoFaMethod(METHOD_AUTHENTICATOR);
            user.setTwoFaSecret(challenge.getPendingSecret()); // already encrypted
            user.setTwoFaLastStep(step);
        } else {
            user.setTwoFaMethod(METHOD_EMAIL);
            user.setTwoFaSecret(null);
            user.setTwoFaLastStep(null);
        }
        userRepository.save(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void confirmDisable(Long userId, String otp) {
        User user = loadUser(userId);
        assertStateAllows(user, Purpose.DISABLE);

        TwoFactorChallenge challenge = findSettingsChallenge(userId, Purpose.DISABLE);
        boolean authenticator = METHOD_AUTHENTICATOR.equals(user.getTwoFaMethod());
        String secret = authenticator ? secretCipher.decrypt(user.getTwoFaSecret()) : null;
        checkCode(challenge, otp, secret, lastStep(user));
        challengeRepository.delete(challenge);

        user.setTwoFaEnabled(false);
        user.setTwoFaMethod(null);
        user.setTwoFaSecret(null);
        user.setTwoFaLastStep(null);
        userRepository.save(user);
    }

    /* ====================================================================== */
    /*  Internals                                                             */
    /* ====================================================================== */

    private void assertStateAllows(User user, Purpose purpose) {
        if (purpose == Purpose.ENABLE && user.isTwoFaActive()) {
            throw new ApiException("2-step verification is already turned on.", HttpStatus.CONFLICT);
        }
        if (purpose == Purpose.DISABLE && !user.isTwoFaActive()) {
            throw new ApiException("2-step verification is already turned off.", HttpStatus.CONFLICT);
        }
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found.", HttpStatus.NOT_FOUND));
    }

    private static long lastStep(User user) {
        return user.getTwoFaLastStep() == null ? -1 : user.getTwoFaLastStep();
    }

    private TwoFactorChallenge findLoginChallenge(String challengeToken) {
        if (challengeToken == null || challengeToken.isBlank()) {
            throw new ApiException(SESSION_EXPIRED, HttpStatus.GONE);
        }
        return challengeRepository
                .findByTokenHashForUpdate(TokenGenerator.hash(challengeToken))
                .orElseThrow(() -> new ApiException(SESSION_EXPIRED, HttpStatus.GONE));
    }

    private TwoFactorChallenge findOrNewSettingsChallenge(Long userId, Purpose purpose) {
        return challengeRepository.findByUserIdAndPurposeForUpdate(userId, purpose)
                .orElseGet(() -> TwoFactorChallenge.builder().userId(userId).purpose(purpose).build());
    }

    private TwoFactorChallenge findSettingsChallenge(Long userId, Purpose purpose) {
        return challengeRepository.findByUserIdAndPurposeForUpdate(userId, purpose)
                .orElseThrow(() -> new ApiException(
                        "Please request a verification code first.", HttpStatus.BAD_REQUEST));
    }

    private void enforceCooldown(TwoFactorChallenge existing) {
        if (existing.getLastSentAt() == null) {
            return;
        }
        long waited = Duration.between(existing.getLastSentAt(), LocalDateTime.now()).getSeconds();
        long remaining = resendCooldownSeconds - waited;
        if (remaining > 0) {
            throw new ApiException(
                    "Please wait " + remaining + " seconds before requesting a new code.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    /** E-mail first, then mutate: if the mail fails nothing about the stored challenge has changed. */
    private void sendAndStore(TwoFactorChallenge challenge, User user, String actionLabel) {
        String otp = TokenGenerator.generateOtp();
        String otpHash = passwordEncoder.encode(otp);

        emailService.sendOtpEmail(user.getEmail(), otp, actionLabel, otpExpiryMinutes);

        challenge.setMethod(METHOD_EMAIL);
        challenge.setPendingSecret(null);
        stamp(challenge, otpHash);
    }

    /** Authenticator challenge: nothing to send; just (re)start the window and the attempt counter. */
    private void startAuthenticatorChallenge(TwoFactorChallenge challenge, String encryptedPendingSecret) {
        challenge.setMethod(METHOD_AUTHENTICATOR);
        challenge.setPendingSecret(encryptedPendingSecret);
        stamp(challenge, NO_OTP);
    }

    private void stamp(TwoFactorChallenge challenge, String otpHash) {
        LocalDateTime now = LocalDateTime.now();
        challenge.setOtpHash(otpHash);
        challenge.setAttempts(0);
        challenge.setLastSentAt(now);
        challenge.setExpiresAt(now.plusMinutes(otpExpiryMinutes));
    }

    /**
     * Validates the code for either method. Returns the matched TOTP time step (authenticator) or -1 (e-mail).
     * Wrong code: attempt counted; at {@code max-attempts} the challenge is deleted.
     */
    private long checkCode(TwoFactorChallenge challenge, String code, String totpSecret, long lastUsedStep) {
        if (challenge.isExpired()) {
            challengeRepository.delete(challenge);
            throw new ApiException("The verification code has expired. Please request a new one.",
                    HttpStatus.GONE);
        }

        boolean authenticator = METHOD_AUTHENTICATOR.equals(challenge.getMethod());
        long step = -1;
        boolean ok;
        if (authenticator) {
            step = TotpUtil.matchStep(totpSecret, code, lastUsedStep);
            ok = step >= 0;
        } else {
            ok = code != null && passwordEncoder.matches(code, challenge.getOtpHash());
        }

        if (!ok) {
            int attempts = challenge.getAttempts() + 1;
            if (attempts >= maxAttempts) {
                challengeRepository.delete(challenge);
                throw new ApiException(
                        "Too many incorrect attempts. Please " + (authenticator ? "log in again." : "request a new code."),
                        HttpStatus.TOO_MANY_REQUESTS);
            }
            challenge.setAttempts(attempts);
            challengeRepository.save(challenge);
            throw new ApiException("Invalid verification code.", HttpStatus.BAD_REQUEST);
        }
        return step;
    }

    /** john.doe@gmail.com -> j***@gmail.com */
    static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
