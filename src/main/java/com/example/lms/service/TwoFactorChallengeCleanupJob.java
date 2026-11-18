package com.example.lms.service;

import com.example.lms.repository.TwoFactorChallengeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Removes OTP challenges that expired and were never completed. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TwoFactorChallengeCleanupJob {

    private final TwoFactorChallengeRepository challengeRepository;

    @Scheduled(cron = "${app.cleanup.two-factor-challenge.cron:0 */30 * * * *}") // every 30 minutes
    @Transactional
    public void purgeExpiredChallenges() {
        int deleted = challengeRepository.deleteExpired(LocalDateTime.now().minusMinutes(5));
        if (deleted > 0) {
            log.info("2FA challenge cleanup: removed {} expired challenge(s).", deleted);
        }
    }
}
