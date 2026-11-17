package com.example.lms.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only aggregate queries for the admin dashboard. Kept in one class (instead of adding
 * dashboard-only methods to five different repositories); every query is scoped to ONE organization
 * (tenant = company admin id).
 */
@Repository
public class AdminDashboardRepository {

    @PersistenceContext
    private EntityManager em;

    /** createdAt of every team member the organization gained in [from, to). */
    public List<LocalDateTime> signupTimes(Long orgId, LocalDateTime from, LocalDateTime to) {
        return em.createQuery(
                        "select u.createdAt from User u " +
                        "where u.companyAdminId = :orgId and u.createdAt >= :from and u.createdAt < :to",
                        LocalDateTime.class)
                .setParameter("orgId", orgId).setParameter("from", from).setParameter("to", to)
                .getResultList();
    }

    /** One row per paid course enrollment in [from, to): [purchasedAt, amount]. */
    public List<Object[]> revenueRows(Long orgId, LocalDateTime from, LocalDateTime to) {
        return em.createQuery(
                        "select p.purchasedAt, p.amount from CoursePurchaseEntity p " +
                        "where p.organizationId = :orgId and p.purchasedAt >= :from and p.purchasedAt < :to",
                        Object[].class)
                .setParameter("orgId", orgId).setParameter("from", from).setParameter("to", to)
                .getResultList();
    }

    /** Team members (without the owner) of the organization with the given role, case-insensitive. */
    public long countTeamByRole(Long orgId, String role) {
        return em.createQuery(
                        "select count(u) from User u " +
                        "where u.companyAdminId = :orgId and upper(u.role) = :role and u.active = true",
                        Long.class)
                .setParameter("orgId", orgId).setParameter("role", role.toUpperCase())
                .getSingleResult();
    }

    /** Users of the organization (owner + team) who are logged in: online = true and seen since {@code since}. */
    public long countOnlineUsers(Long orgId, LocalDateTime since) {
        return em.createQuery(
                        "select count(u) from User u " +
                        "where u.online = true and u.lastSeenAt >= :since " +
                        "and (u.companyAdminId = :orgId or u.id = :orgId)", Long.class)
                .setParameter("orgId", orgId).setParameter("since", since)
                .getSingleResult();
    }

    public long countEnrollments(Long orgId) {
        return em.createQuery(
                        "select count(e) from EnrollmentEntity e, CourseEntity c " +
                        "where c.id = e.courseId and c.organizationId = :orgId", Long.class)
                .setParameter("orgId", orgId).getSingleResult();
    }

    public long countCompletedEnrollments(Long orgId) {
        return em.createQuery(
                        "select count(e) from EnrollmentEntity e, CourseEntity c " +
                        "where c.id = e.courseId and c.organizationId = :orgId and e.completedAt is not null",
                        Long.class)
                .setParameter("orgId", orgId).getSingleResult();
    }

    /** [enrolledAt, completedAt] of every completed enrollment of the organization. */
    public List<Object[]> completedEnrollmentSpans(Long orgId) {
        return em.createQuery(
                        "select e.enrolledAt, e.completedAt from EnrollmentEntity e, CourseEntity c " +
                        "where c.id = e.courseId and c.organizationId = :orgId and e.completedAt is not null",
                        Object[].class)
                .setParameter("orgId", orgId).getResultList();
    }
}