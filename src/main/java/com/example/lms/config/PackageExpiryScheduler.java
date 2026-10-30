package com.example.lms.config;

import com.example.lms.service.PackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes a package from an admin once its duration has ended.
 * Example: 1-day package assigned today 12:30 -> removed tomorrow 12:30.
 *
 * Needs @EnableScheduling on the main application class.
 */
@Component
@RequiredArgsConstructor
public class PackageExpiryScheduler {

    private final PackageService packageService;

    /** Runs at second 0 of every minute. */
    @Scheduled(cron = "0 * * * * *")
    public void removeExpiredPackages() {
        packageService.expireDuePackages();
    }
}