package com.example.lms.service;

import com.example.lms.entity.StudyTimeEntity;
import com.example.lms.entity.User;
import com.example.lms.repository.EnrollmentRepository;
import com.example.lms.repository.StudyTimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Study time per learner per course (Course Report "Total Time Spent", Learner Report "Study Time").
 *
 * The browser pings every 30 s while the course content page is open AND the tab is visible.
 * The seconds added are measured by the server (now - previous ping), so:
 *  - a closed / hidden tab adds nothing (the gap is too long, the next ping only restarts the clock),
 *  - pinging faster can never add more than real elapsed time.
 *
 * Deliberately not @Transactional: the first-ping insert may lose a race against another tab
 * (unique constraint) and that must be swallowed without poisoning an outer transaction.
 */
@Service
@RequiredArgsConstructor
public class StudyTimeService {

    /** A gap longer than this = the learner was away; the clock restarts instead of counting the gap. */
    private static final long MAX_GAP_SECONDS = 45;
    /** Most one ping can ever add. */
    private static final long MAX_ADD_SECONDS = 45;

    private final CourseAccessService courseAccess;
    private final EnrollmentRepository enrollmentRepository;
    private final StudyTimeRepository studyTimeRepository;

    public void ping(Long courseId) {
        User user = courseAccess.currentUser();
        // Only an enrolled learner studies. Admins / instructors previewing a course are not counted.
        if (!enrollmentRepository.existsByCourseIdAndUserId(courseId, user.getId())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        StudyTimeEntity row = studyTimeRepository.findByUserIdAndCourseId(user.getId(), courseId).orElse(null);

        if (row == null) {
            try {
                studyTimeRepository.saveAndFlush(new StudyTimeEntity(user.getId(), courseId, now));
            } catch (DataIntegrityViolationException raced) {
                // another tab created it first - nothing to add on a first ping anyway
            }
            return;
        }

        long gap = Duration.between(row.getLastPingAt(), now).getSeconds();
        long delta = (gap >= 1 && gap <= MAX_GAP_SECONDS) ? Math.min(gap, MAX_ADD_SECONDS) : 0;
        studyTimeRepository.addSeconds(row.getId(), delta, now);
    }
}