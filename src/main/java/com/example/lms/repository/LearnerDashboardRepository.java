package com.example.lms.repository;

import com.example.lms.entity.QuizAttemptEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only queries for the learner dashboard. Every query is for ONE learner (userId) and joins the
 * courses of ONE organization, so a learner never sees another organization's data.
 */
@Repository
public class LearnerDashboardRepository {

    @PersistenceContext
    private EntityManager em;

    /** [status, count] over the learner's enrollments. */
    public List<Object[]> enrollmentCountsByStatus(Long userId, Long orgId) {
        return em.createQuery("""
                select e.status, count(e.id)
                from EnrollmentEntity e
                join CourseEntity c on c.id = e.courseId
                where e.userId = :userId and c.organizationId = :orgId
                group by e.status
                """, Object[].class)
                .setParameter("userId", userId)
                .setParameter("orgId", orgId)
                .getResultList();
    }

    /** [courseId, courseName] of the courses the learner is enrolled in, A-Z. */
    public List<Object[]> enrolledCourses(Long userId, Long orgId) {
        return em.createQuery("""
                select c.id, c.name
                from EnrollmentEntity e
                join CourseEntity c on c.id = e.courseId
                where e.userId = :userId and c.organizationId = :orgId
                order by c.name asc, c.id asc
                """, Object[].class)
                .setParameter("userId", userId)
                .setParameter("orgId", orgId)
                .getResultList();
    }

    /**
     * [courseId, courseName, quizName, score, maxScore] of every SUBMITTED quiz, oldest first.
     * quiz_attempts holds one row per learner per quiz (a retest re-uses it), so this is the latest result.
     */
    public List<Object[]> submittedQuizScores(Long userId, Long orgId) {
        return em.createQuery("""
                select a.courseId, c.name, b.name, a.score, a.maxScore
                from QuizAttemptEntity a
                join CourseEntity c on c.id = a.courseId
                join QuestionBankEntity b on b.id = a.bankId
                where a.userId = :userId
                  and c.organizationId = :orgId
                  and a.status = :submitted
                  and a.score is not null
                  and a.maxScore is not null
                  and a.maxScore > 0
                order by a.submittedAt asc, a.id asc
                """, Object[].class)
                .setParameter("userId", userId)
                .setParameter("orgId", orgId)
                .setParameter("submitted", QuizAttemptEntity.SUBMITTED)
                .getResultList();
    }

    /** [courseId, courseName, seconds studied] of the courses the learner has study time in, A-Z. */
    public List<Object[]> studySecondsPerCourse(Long userId, Long orgId) {
        return em.createQuery("""
                select c.id, c.name, s.seconds
                from StudyTimeEntity s
                join CourseEntity c on c.id = s.courseId
                where s.userId = :userId
                  and c.organizationId = :orgId
                  and s.seconds > 0
                order by c.name asc, c.id asc
                """, Object[].class)
                .setParameter("userId", userId)
                .setParameter("orgId", orgId)
                .getResultList();
    }
}