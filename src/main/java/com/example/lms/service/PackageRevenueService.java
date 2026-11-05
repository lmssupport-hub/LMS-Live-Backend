package com.example.lms.service;

import com.example.lms.dto.PackageRevenueDto;
import com.example.lms.entity.PackagePurchaseEntity;
import com.example.lms.repository.PackagePurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class PackageRevenueService {

    private final PackagePurchaseRepository purchaseRepository;

    /**
     * Daily revenue points, oldest first. from/to (both inclusive) are optional;
     * with neither, the whole history is returned.
     */
    @Transactional(readOnly = true)
    public List<PackageRevenueDto.Point> getDailyRevenue(LocalDate from, LocalDate to) {
        List<PackagePurchaseEntity> purchases;
        if (from != null && to != null) {
            purchases = purchaseRepository
                    .findByPurchasedAtGreaterThanEqualAndPurchasedAtLessThanOrderByPurchasedAtAsc(
                            from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        } else {
            purchases = purchaseRepository.findAllByOrderByPurchasedAtAsc();
        }

        Map<LocalDate, double[]> byDay = new TreeMap<>(); // [0] = amount, [1] = count
        for (PackagePurchaseEntity purchase : purchases) {
            double[] bucket = byDay.computeIfAbsent(purchase.getPurchasedAt().toLocalDate(), d -> new double[2]);
            bucket[0] += purchase.getPrice() == null ? 0 : purchase.getPrice();
            bucket[1] += 1;
        }

        return byDay.entrySet().stream()
                .map(e -> new PackageRevenueDto.Point(e.getKey().toString(), e.getValue()[0], (long) e.getValue()[1]))
                .toList();
    }
}