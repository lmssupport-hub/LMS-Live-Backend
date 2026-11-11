package com.example.lms.service;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.PermissionDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.EnrollmentRepository;
import com.example.lms.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NEW - single place for Multi-Tenant Data Isolation of courses.
 *
 * Tenant (organization) of a user = PackageService.resolveOwnerId(user):
 *   - company ADMIN (self-signup)  -> the admin's own id
 *   - invited team member          -> user.companyAdminId
 * Every course carries that id in courses.organization_id, and every course / section /
 * multimedia / question-bank / enrollment endpoint goes through this class, so a user can
 * never read or change another organization's course (a foreign course answers 404).
 */
@Service
public class CourseAccessService {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PermissionService permissionService;

    public CourseAccessService(UserRepository userRepository,
                               CourseRepository courseRepository,
                               EnrollmentRepository enrollmentRepository,
                               PermissionService permissionService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.permissionService = permissionService;
    }

    // ---------- who is calling ----------

    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ApiException("Authentication required.", HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException("User not found.", HttpStatus.UNAUTHORIZED));
    }

    public Long currentOrganizationId() {
        return PackageService.resolveOwnerId(currentUser());
    }

    /**
     * Can this user manage courses (create / edit / delete / organize / enrol others)?
     * Same rule the Course List used before; a user whose role is literally LEARNER never manages.
     */
    public boolean isManager(User user) {
        if ("LEARNER".equalsIgnoreCase(user.getRole())) {
            return false;
        }
        PermissionDto.Snapshot access = permissionService.snapshotOf(user);
        return access.isFullAccess()
                || (access.isCompanyAdmin() && access.allows(PermissionService.COURSE_LIST, "read"))
                || access.allows(PermissionService.COURSE_CREATE, "create")
                || access.allows(PermissionService.COURSE_CREATE, "update")
                || access.allows(PermissionService.COURSE_CREATE, "delete")
                || access.allows(PermissionService.COURSE_ORGANIZE, "update");
    }

    /** May this user open the Course List at all (as a learner-style viewer or as a manager)? */
    public boolean canViewCourseList(User user) {
        if ("LEARNER".equalsIgnoreCase(user.getRole())) {
            return true;
        }
        return isManager(user)
                || permissionService.snapshotOf(user).allows(PermissionService.COURSE_LIST, "read");
    }

    // ---------- course lookups ----------

    /** The course, only if it belongs to the caller's organization. Otherwise 404 (never reveals other tenants). */
    @Transactional(readOnly = true)
    public CourseEntity requireCourseInOrganization(Long courseId) {
        if (courseId == null) {
            throw notFound(courseId);
        }
        Long organizationId = currentOrganizationId();
        return courseRepository.findByIdAndOrganizationId(courseId, organizationId)
                .orElseThrow(() -> notFound(courseId));
    }

    /**
     * For READ access to a course and its content:
     *  - manager: any course of the organization
     *  - everyone else: only a PUBLISHED course they are enrolled in
     */
    @Transactional(readOnly = true)
    public CourseEntity requireReadableCourse(Long courseId) {
        User user = currentUser();
        if (courseId == null) {
            throw notFound(courseId);
        }
        CourseEntity course = courseRepository
                .findByIdAndOrganizationId(courseId, PackageService.resolveOwnerId(user))
                .orElseThrow(() -> notFound(courseId));
        if (isManager(user)) {
            return course;
        }
        boolean published = "PUBLISHED".equalsIgnoreCase(course.getStatus());
        if (!published || !enrollmentRepository.existsByCourseIdAndUserId(courseId, user.getId())) {
            // not enrolled yet (or not published): a learner must enrol first
            throw new ApiException("Please enroll in this course to view its content.", HttpStatus.FORBIDDEN);
        }
        return course;
    }

    private ApiException notFound(Long courseId) {
        return new ApiException("Course not found with id: " + courseId, HttpStatus.NOT_FOUND);
    }
}
