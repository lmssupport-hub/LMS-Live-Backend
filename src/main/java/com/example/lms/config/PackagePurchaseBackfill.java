package com.example.lms.config;

import com.example.lms.entity.PackageEntity;
import com.example.lms.entity.PackagePurchaseEntity;
import com.example.lms.entity.User;
import com.example.lms.repository.PackagePurchaseRepository;
import com.example.lms.repository.PackageRepository;
import com.example.lms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time (idempotent) backfill: admins who already hold a package before the
 * package_purchases table existed get one purchase row from their current assignment,
 * so today's revenue is not lost. Safe to run on every startup.
 */
@Component
@RequiredArgsConstructor
public class PackagePurchaseBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PackagePurchaseBackfill.class);

    private final UserRepository userRepository;
    private final PackageRepository packageRepository;
    private final PackagePurchaseRepository purchaseRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (User user : userRepository.findAll()) {
            if (user.getPackageId() == null || user.getPackageAssignedAt() == null) {
                continue;
            }
            if (purchaseRepository.existsByUserIdAndPurchasedAt(user.getId(), user.getPackageAssignedAt())) {
                continue;
            }
            PackageEntity pkg = packageRepository.findById(user.getPackageId()).orElse(null);
            if (pkg == null) {
                continue;
            }
            PackagePurchaseEntity purchase = new PackagePurchaseEntity();
            purchase.setUserId(user.getId());
            purchase.setPackageId(pkg.getId());
            purchase.setPackageName(pkg.getName());
            purchase.setPrice(pkg.getPrice());
            purchase.setPurchasedAt(user.getPackageAssignedAt());
            purchaseRepository.save(purchase);
            created++;
        }
        if (created > 0) {
            log.info("Backfilled {} package purchase(s) into package_purchases", created);
        }
    }
}