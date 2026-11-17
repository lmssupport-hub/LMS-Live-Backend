package com.example.lms.service;

import com.example.lms.dto.AdminDashboardDto;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.AdminDashboardRepository;
import com.example.lms.repository.LoginEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Data of the admin dashboard home, scoped to the caller's organization (tenant = company admin id,
 * the same rule CourseAccessService uses everywhere else).
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    /** No heartbeat (sent every ~45 s) for this long = the browser is closed -> offline. */
    private static final int ONLINE_TIMEOUT_SECONDS = 120;

    private final CourseAccessService courseAccess;
    private final AdminDashboardRepository queries;
    private final LoginEventRepository loginEvents;

    /** A chart bucket: [start, end) with its axis label. */
    private record Bucket(LocalDateTime start, LocalDateTime end, String label) { }

    @Transactional(readOnly = true)
    public AdminDashboardDto.Response getDashboard(String periodParam) {
        User user = courseAccess.currentUser();
        if ("LEARNER".equalsIgnoreCase(user.getRole())) {
            throw new ApiException("Learners do not have an admin dashboard.", HttpStatus.FORBIDDEN);
        }
        Long orgId = PackageService.resolveOwnerId(user);
        String period = normalizePeriod(periodParam);
        List<Bucket> buckets = buckets(period, LocalDate.now());
        LocalDateTime from = buckets.get(0).start();
        LocalDateTime to = buckets.get(buckets.size() - 1).end();

        double[] logins = new double[buckets.size()];
        for (LocalDateTime t : loginEvents.findTimes(orgId, from, to)) {
            logins[indexOf(buckets, t)]++;
        }

        double[] signups = new double[buckets.size()];
        for (LocalDateTime t : queries.signupTimes(orgId, from, to)) {
            signups[indexOf(buckets, t)]++;
        }

        double[] revenue = new double[buckets.size()];
        for (Object[] row : queries.revenueRows(orgId, from, to)) {
            BigDecimal amount = (BigDecimal) row[1];
            revenue[indexOf(buckets, (LocalDateTime) row[0])] += amount == null ? 0 : amount.doubleValue();
        }

        long enrollments = queries.countEnrollments(orgId);
        long completed = queries.countCompletedEnrollments(orgId);
        double completionRate = enrollments == 0 ? 0 : Math.round(completed * 1000.0 / enrollments) / 10.0;

        return AdminDashboardDto.Response.builder()
                .period(period)
                .logins(series(buckets, logins))
                .newSignups(series(buckets, signups))
                .revenue(series(buckets, revenue))
                .userDistribution(new AdminDashboardDto.UserDistribution(
                        1, // the company admin (owner) of this organization
                        queries.countTeamByRole(orgId, "INSTRUCTOR"),
                        queries.countTeamByRole(orgId, "LEARNER")))
                .courseCompletionRate(completionRate)
                .onlineUsers(queries.countOnlineUsers(orgId, LocalDateTime.now().minusSeconds(ONLINE_TIMEOUT_SECONDS)))
                .averageLearningTime(averageLearningTime(orgId))
                .build();
    }

    // ---------- helpers ----------

    private static String normalizePeriod(String value) {
        String p = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (p) {
            case "WEEK", "YEAR" -> p;
            default -> "MONTH";
        };
    }

    /**
     * WEEK  -> last 7 days, one bucket per day (Mon, Tue, ...)
     * MONTH -> last 30 days, 6 buckets of 5 days ("12 Nov")
     * YEAR  -> last 12 months, one bucket per month (Jan, Feb, ...)
     */
    private static List<Bucket> buckets(String period, LocalDate today) {
        List<Bucket> list = new ArrayList<>();
        switch (period) {
            case "WEEK" -> {
                for (int i = 6; i >= 0; i--) {
                    LocalDate d = today.minusDays(i);
                    list.add(new Bucket(d.atStartOfDay(), d.plusDays(1).atStartOfDay(),
                            d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)));
                }
            }
            case "YEAR" -> {
                LocalDate first = today.withDayOfMonth(1).minusMonths(11);
                for (int i = 0; i < 12; i++) {
                    LocalDate m = first.plusMonths(i);
                    list.add(new Bucket(m.atStartOfDay(), m.plusMonths(1).atStartOfDay(),
                            m.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)));
                }
            }
            default -> {
                LocalDate first = today.minusDays(29);
                for (int i = 0; i < 6; i++) {
                    LocalDate s = first.plusDays(i * 5L);
                    list.add(new Bucket(s.atStartOfDay(), s.plusDays(5).atStartOfDay(),
                            s.getDayOfMonth() + " " + s.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)));
                }
            }
        }
        return list;
    }

    private static int indexOf(List<Bucket> buckets, LocalDateTime t) {
        for (int i = 0; i < buckets.size(); i++) {
            Bucket b = buckets.get(i);
            if (!t.isBefore(b.start()) && t.isBefore(b.end())) {
                return i;
            }
        }
        return buckets.size() - 1; // never reached: queries are limited to [first.start, last.end)
    }

    private static AdminDashboardDto.Series series(List<Bucket> buckets, double[] values) {
        List<String> labels = buckets.stream().map(Bucket::label).toList();
        List<Double> list = new ArrayList<>(values.length);
        double total = 0;
        for (double v : values) {
            double rounded = Math.round(v * 100.0) / 100.0;
            list.add(rounded);
            total += rounded;
        }
        return new AdminDashboardDto.Series(labels, list, Math.round(total * 100.0) / 100.0);
    }

    /** Average enrollment -> completion duration, e.g. "2d 4h", "5h 30m", "0m". */
    private String averageLearningTime(Long orgId) {
        List<Object[]> spans = queries.completedEnrollmentSpans(orgId);
        if (spans.isEmpty()) {
            return "0m";
        }
        long totalMinutes = 0;
        for (Object[] row : spans) {
            totalMinutes += Math.max(0, Duration.between((LocalDateTime) row[0], (LocalDateTime) row[1]).toMinutes());
        }
        long avg = totalMinutes / spans.size();
        long days = avg / (24 * 60);
        long hours = (avg % (24 * 60)) / 60;
        long minutes = avg % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}