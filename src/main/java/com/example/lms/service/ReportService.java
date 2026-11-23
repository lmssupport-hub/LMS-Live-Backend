package com.example.lms.service;

import com.example.lms.dto.ReportDto;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin > Reports (Course Report + Learner Report).
 *
 * Tenant rule is the same as everywhere else: organization = PackageService.resolveOwnerId(caller).
 * Only users who can manage courses may open reports (learner personal data is shown here).
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** Same list the Course List / Create Course form uses (CourseService.CATEGORIES). */
    private static final Map<Integer, String> CATEGORIES = Map.of(
            1, "Technical",
            2, "Soft Skills",
            3, "Compliance",
            4, "Leadership"
    );

    private final CourseAccessService courseAccess;
    private final ReportRepository queries;

    // ---------------------------------------------------------------- Course Report

    @Transactional(readOnly = true)
    public List<ReportDto.CourseRow> getCourseReport() {
        Long orgId = requireReportAccess();

        Map<Long, Long> certificates = new HashMap<>();
        for (Object[] r : queries.certificatesPerCourse(orgId)) {
            certificates.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }

        Map<Long, Long> studySeconds = new HashMap<>();
        for (Object[] r : queries.studySecondsPerCourse(orgId)) {
            studySeconds.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }

        List<ReportDto.CourseRow> rows = new ArrayList<>();
        for (Object[] r : queries.courseAggregates(orgId)) {
            long courseId = ((Number) r[0]).longValue();
            Integer categoryId = r[2] == null ? null : ((Number) r[2]).intValue();
            long total = ((Number) r[3]).longValue();
            long completed = ((Number) r[4]).longValue();
            long inProgress = ((Number) r[5]).longValue();
            long notStarted = ((Number) r[6]).longValue();
            double avgProgress = r[7] == null ? 0 : ((Number) r[7]).doubleValue();

            rows.add(ReportDto.CourseRow.builder()
                    .id(courseId)
                    .courseTitle((String) r[1])
                    .courseCode(String.format("CRS-%04d", courseId))
                    .category(CATEGORIES.getOrDefault(categoryId, "Uncategorized"))
                    .enrollments(total)
                    .completionRate(percent(completed, total))
                    .learners(total)
                    .completedLearners(completed)
                    .learnersInProgress(inProgress)
                    .learnersNotStarted(notStarted)
                    .avgUserProgress((int) Math.round(avgProgress))
                    .totalTimeSpent(formatDuration(studySeconds.getOrDefault(courseId, 0L)))
                    .certificatesIssued(certificates.getOrDefault(courseId, 0L))
                    .build());
        }
        return rows;
    }

    // ---------------------------------------------------------------- Learner Report

    @Transactional(readOnly = true)
    public List<ReportDto.LearnerRow> getLearnerReport() {
        Long orgId = requireReportAccess();

        // userId -> [enrolled, completed, incomplete, avgScore]
        Map<Long, Object[]> stats = new HashMap<>();
        for (Object[] r : queries.enrollmentStatsPerLearner(orgId)) {
            stats.put(((Number) r[0]).longValue(), r);
        }

        Map<Long, LocalDateTime> lastLogin = toMap(queries.lastLoginPerUser(orgId));

        // Same number for every learner of the organization.
        long assignedCourses = queries.publishedCourseCount(orgId);

        Map<Long, Long> learnerStudySeconds = new HashMap<>();
        for (Object[] r : queries.studySecondsPerLearner(orgId)) {
            learnerStudySeconds.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }

        // Last activity = the newest of content progress, quiz attempt and module progress.
        Map<Long, LocalDateTime> lastActivity = new HashMap<>();
        mergeLatest(lastActivity, queries.lastContentActivity(orgId));
        mergeLatest(lastActivity, queries.lastQuizActivity(orgId));
        mergeLatest(lastActivity, queries.lastSectionActivity(orgId));

        List<ReportDto.LearnerRow> rows = new ArrayList<>();
        for (Object[] u : queries.learners(orgId)) {
            Long userId = ((Number) u[0]).longValue();
            Object[] s = stats.get(userId);

            long enrolled = s == null ? 0 : ((Number) s[1]).longValue();
            long completed = s == null ? 0 : ((Number) s[2]).longValue();
            long incomplete = s == null ? 0 : ((Number) s[3]).longValue();
            Double avgScore = (s == null || s[4] == null) ? null
                    : BigDecimal.valueOf(((Number) s[4]).doubleValue()).setScale(2, RoundingMode.HALF_UP).doubleValue();

            rows.add(ReportDto.LearnerRow.builder()
                    .userId(userId)
                    .learner((u[1] + " " + u[2]).trim())
                    .role((String) u[3])
                    .department(blankToNull((String) u[4]))
                    .registrationDate((LocalDateTime) u[5])
                    .lastActivity(lastActivity.get(userId))
                    .lastLogin(lastLogin.get(userId))
                    .completedCourses(completed)
                    .incompleteCourses(incomplete)
                    .enrolledCourses(enrolled)
                    .assignedCourses(assignedCourses)
                    .averageScore(avgScore)
                    .studyTime(formatDuration(learnerStudySeconds.getOrDefault(userId, 0L)))
                    .totalTimeOnPlatform(formatDuration(u[6] == null ? 0L : ((Number) u[6]).longValue()))
                    .build());
        }
        return rows;
    }

    // ---------------------------------------------------------------- helpers

    private Long requireReportAccess() {
        User user = courseAccess.currentUser();
        if (!courseAccess.isManager(user)) {
            throw new ApiException("You do not have permission to view reports.", HttpStatus.FORBIDDEN);
        }
        return PackageService.resolveOwnerId(user);
    }

    /** 0 -> "0m", 45 -> "<1m", 1500 -> "25m", 7500 -> "2h 05m". */
    static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "0m";
        }
        if (seconds < 60) {
            return "<1m";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours > 0 ? String.format("%dh %02dm", hours, minutes) : minutes + "m";
    }

    private static int percent(long part, long total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<Long, LocalDateTime> toMap(List<Object[]> rows) {
        Map<Long, LocalDateTime> map = new HashMap<>();
        mergeLatest(map, rows);
        return map;
    }

    private static void mergeLatest(Map<Long, LocalDateTime> target, List<Object[]> rows) {
        for (Object[] r : rows) {
            if (r[1] == null) {
                continue;
            }
            Long userId = ((Number) r[0]).longValue();
            LocalDateTime at = (LocalDateTime) r[1];
            target.merge(userId, at, (a, b) -> a.isAfter(b) ? a : b);
        }
    }
}