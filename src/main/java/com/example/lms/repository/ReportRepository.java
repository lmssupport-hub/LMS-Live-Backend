package com.example.lms.repository;

import com.example.lms.entity.EnrollmentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only aggregate queries for Admin > Reports. Same style as AdminDashboardRepository:
 * one class, every query scoped to ONE organization (tenant = company admin id), and all
 * counting is done by the database (GROUP BY) - never one request per course / learner.
 */
@Repository
public class ReportRepository {

    @PersistenceContext
    private EntityManager em;

    // ---------------------------------------------------------------- Course Report

    /**
     * One row per course of the organization (courses with 0 enrollments included - LEFT JOIN):
     * [courseId, name, categoryId, enrollments, completed, inProgress, notStarted, avgProgress]
     */
    public List<Object[]> courseAggregates(Long orgId) {
        return em.createQuery("""
                select c.id, c.name, c.categoryId,
                       count(e.id),
                       coalesce(sum(case when e.status = :completed  then 1 else 0 end), 0L),
                       coalesce(sum(case when e.status = :inProgress then 1 else 0 end), 0L),
                       coalesce(sum(case when e.status = :notStarted then 1 else 0 end), 0L),
                       avg(e.progressPercent)
                from CourseEntity c
                left join EnrollmentEntity e on e.courseId = c.id
                where c.organizationId = :orgId
                group by c.id, c.name, c.categoryId
                order by c.name asc, c.id asc
                """, Object[].class)
                .setParameter("orgId", orgId)
                .setParameter("completed", EnrollmentStatus.COMPLETED)
                .setParameter("inProgress", EnrollmentStatus.IN_PROGRESS)
                .setParameter("notStarted", EnrollmentStatus.NOT_STARTED)
                .getResultList();
    }

    /** [courseId, certificatesIssued] for every course of the organization that has any. */
    public List<Object[]> certificatesPerCourse(Long orgId) {
        return em.createQuery("""
                select ic.courseId, count(ic.id)
                from IssuedCertificateEntity ic
                where ic.organizationId = :orgId
                group by ic.courseId
                """, Object[].class)
                .setParameter("orgId", orgId)
                .getResultList();
    }

    // ---------------------------------------------------------------- Learner Report

    /** Active-or-not, but not deleted, learners of the organization: [id, firstName, lastName, role, department, createdAt, platformSeconds] */
    public List<Object[]> learners(Long orgId) {
        return em.createQuery("""
                select u.id, u.firstName, u.lastName, u.role, u.department, u.createdAt, u.platformSeconds
                from User u
                where u.companyAdminId = :orgId
                  and upper(u.role) = 'LEARNER'
                  and u.deletedAt is null
                order by u.firstName asc, u.lastName asc, u.id asc
                """, Object[].class)
                .setParameter("orgId", orgId)
                .getResultList();
    }

    /**
     * Per learner, over the organization's courses only:
     * [userId, enrolled, completed, incomplete, avgScore]
     */
    public List<Object[]> enrollmentStatsPerLearner(Long orgId) {
        return em.createQuery("""
                select e.userId,
                       count(e.id),
                       coalesce(sum(case when e.status = :completed then 1 else 0 end), 0L),
                       coalesce(sum(case when e.status <> :completed then 1 else 0 end), 0L),
                       avg(e.scorePercent)
                from EnrollmentEntity e
                join CourseEntity c on c.id = e.courseId
                where c.organizationId = :orgId
                group by e.userId
                """, Object[].class)
                .setParameter("orgId", orgId)
                .setParameter("completed", EnrollmentStatus.COMPLETED)
                .getResultList();
    }

    /** [courseId, seconds studied by all learners] for the organization's courses. */
    public List<Object[]> studySecondsPerCourse(Long orgId) {
        return em.createQuery("""
                select s.courseId, sum(s.seconds)
                from StudyTimeEntity s
                join CourseEntity c on c.id = s.courseId
                where c.organizationId = :orgId
                group by s.courseId
                """, Object[].class)
                .setParameter("orgId", orgId)
                .getResultList();
    }

    /** [userId, seconds studied] per learner, over the organization's courses only. */
    public List<Object[]> studySecondsPerLearner(Long orgId) {
        return em.createQuery("""
                select s.userId, sum(s.seconds)
                from StudyTimeEntity s
                join CourseEntity c on c.id = s.courseId
                where c.organizationId = :orgId
                group by s.userId
                """, Object[].class)
                .setParameter("orgId", orgId)
                .getResultList();
    }

    /** [userId, lastLoginAt] */
    public List<Object[]> lastLoginPerUser(Long orgId) {
        return em.createQuery("""
                select l.userId, max(l.loggedInAt)
                from LoginEventEntity l
                where l.organizationId = :orgId
                group by l.userId
                """, Object[].class)
                .setParameter("orgId", orgId)
                .getResultList();
    }

    /** [userId, latest content progress update] */
    public List<Object[]> lastContentActivity(Long orgId) {
        return em.createQuery("""
                select p.userId, max(p.updatedAt)
                from ContentProgressEntity p
                where p.userId in (select u.id from User u where u.companyAdminId = :orgId)
                group by p.userId
                """, Object[].class)
                .setParameter("orgId", orgId)
                .getResultList();
    }

    /** [userId, latest quiz start / submit] */
    public List<Object[]> lastQuizActivity(Long orgId) {
        return em.createQuery("""
                select q.userId, max(coalesce(q.submittedAt, q.startedAt))
                from QuizAttemptEntity q
                where q.userId in (select u.id from User u where u.companyAdminId = :orgId)
                group by q.userId
                """, Object[].class)
                .setParameter("orgId", orgId)
                .getResultList();
    }

    /** [userId, latest module start / completion] */
    public List<Object[]> lastSectionActivity(Long orgId) {
        return em.createQuery("""
                select s.userId, max(coalesce(s.completedAt, s.startedAt))
                from SectionProgressEntity s
                where s.userId in (select u.id from User u where u.companyAdminId = :orgId)
                group by s.userId
                """, Object[].class)
                .setParameter("orgId", orgId)
                .getResultList();
    }
}