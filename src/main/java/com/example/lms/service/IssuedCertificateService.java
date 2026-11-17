package com.example.lms.service;

import com.example.lms.dto.MyCertificateDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.EnrollmentEntity;
import com.example.lms.entity.EnrollmentStatus;
import com.example.lms.entity.IssuedCertificateEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.EnrollmentRepository;
import com.example.lms.repository.IssuedCertificateRepository;
import com.example.lms.repository.OrganizationDetailsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * "My Certificate" for a learner.
 *
 * A certificate exists for every course the learner has COMPLETED (course_enrollments.status = COMPLETED).
 * It is issued the first time the learner opens My Certificate after completing (see syncCompleted), so
 * courses completed before this feature existed get their certificate too. The completion date is the
 * enrollment's completedAt.
 */
@Service
public class IssuedCertificateService {

    private static final Logger log = LoggerFactory.getLogger(IssuedCertificateService.class);

    private final CourseAccessService courseAccess;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final OrganizationDetailsRepository organizationRepository;
    private final IssuedCertificateRepository certificateRepository;
    private final CertificateTemplateService templateService;

    public IssuedCertificateService(CourseAccessService courseAccess,
                                    EnrollmentRepository enrollmentRepository,
                                    CourseRepository courseRepository,
                                    OrganizationDetailsRepository organizationRepository,
                                    IssuedCertificateRepository certificateRepository,
                                    CertificateTemplateService templateService) {
        this.courseAccess = courseAccess;
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.organizationRepository = organizationRepository;
        this.certificateRepository = certificateRepository;
        this.templateService = templateService;
    }

    /** The caller's certificates, newest first (issues any that are missing first). */
    @Transactional
    public List<MyCertificateDto.ListItem> listMine() {
        User learner = courseAccess.currentUser();
        syncCompleted(learner);
        return certificateRepository.findByUserIdOrderByIssuedAtDescIdDesc(learner.getId()).stream()
                .map(c -> MyCertificateDto.ListItem.builder()
                        .id(c.getId())
                        .courseId(c.getCourseId())
                        .courseName(c.getCourseName())
                        .certificateNumber(c.getCertificateNumber())
                        .issuedAt(c.getIssuedAt())
                        .build())
                .toList();
    }

    /** One certificate of the caller (never someone else's) with the design to draw it with. */
    @Transactional(readOnly = true)
    public MyCertificateDto.Detail getMine(Long certificateId) {
        User learner = courseAccess.currentUser();
        IssuedCertificateEntity c = certificateRepository.findByIdAndUserId(certificateId, learner.getId())
                .orElseThrow(() -> new ApiException("Certificate not found.", HttpStatus.NOT_FOUND));
        return MyCertificateDto.Detail.builder()
                .id(c.getId())
                .courseId(c.getCourseId())
                .courseName(c.getCourseName())
                .learnerName(c.getLearnerName())
                .organizationName(c.getOrganizationName())
                .certificateNumber(c.getCertificateNumber())
                .issuedAt(c.getIssuedAt())
                .template(templateService.findForOrganization(c.getOrganizationId())
                        .map(CertificateTemplateMapper::toResponse)
                        .orElse(null))
                .build();
    }

    // ------------------------------------------------------------------

    /** Issues a certificate for every COMPLETED course of the learner that does not have one yet. */
    private void syncCompleted(User learner) {
        Set<Long> have = certificateRepository.findCourseIdsByUserId(learner.getId());
        for (EnrollmentEntity enrollment : enrollmentRepository.findByUserId(learner.getId())) {
            if (enrollment.getStatus() != EnrollmentStatus.COMPLETED || have.contains(enrollment.getCourseId())) {
                continue;
            }
            CourseEntity course = courseRepository.findById(enrollment.getCourseId()).orElse(null);
            if (course == null) {
                log.warn("Skipping certificate: course {} no longer exists (user {})", enrollment.getCourseId(), learner.getId());
                continue;
            }
            issue(learner, course, enrollment.getCompletedAt() != null ? enrollment.getCompletedAt() : LocalDateTime.now());
        }
    }

    private void issue(User learner, CourseEntity course, LocalDateTime completedAt) {
        Long organizationId = PackageService.resolveOwnerId(learner);

        IssuedCertificateEntity c = new IssuedCertificateEntity();
        c.setUserId(learner.getId());
        c.setCourseId(course.getId());
        c.setOrganizationId(organizationId);
        c.setLearnerName((learner.getFirstName() + " " + learner.getLastName()).trim());
        c.setCourseName(course.getName());
        c.setOrganizationName(organizationRepository.findByUserId(organizationId)
                .map(o -> o.getOrganizationName()).orElse(null));
        c.setIssuedAt(completedAt);
        // Per-organization number: every organization counts 1, 2, 3... on its own.
        // The organization is locked while the next number is taken, so the same number is never given twice.
        certificateRepository.lockOrganization(organizationId);
        int next = certificateRepository.findMaxSequenceNo(organizationId) + 1;
        c.setSequenceNo(next);
        c.setCertificateNumber(String.format("CERT-%d-%d-%06d", organizationId, completedAt.getYear(), next));
        certificateRepository.saveAndFlush(c);
    }
}