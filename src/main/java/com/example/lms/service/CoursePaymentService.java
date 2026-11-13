package com.example.lms.service;

import com.example.lms.dto.CoursePaymentDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.CoursePurchaseEntity;
import com.example.lms.entity.EnrollmentEntity;
import com.example.lms.entity.EnrollmentStatus;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.exception.RateLimitExceededException;
import com.example.lms.repository.CoursePurchaseRepository;
import com.example.lms.repository.EnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Learner pays for a PAID course and is enrolled in the same transaction.
 *
 * This is deliberately NOT the package payment (PackageService.purchasePackage):
 * that one is for the company admin buying a package; this one is for a learner buying a course.
 * Same Payment page design on the front end, separate endpoint + ledger + rules here.
 */
@Service
public class CoursePaymentService {

    private static final Logger log = LoggerFactory.getLogger(CoursePaymentService.class);

    // Test-mode gateway (same test values as the package payment until a real gateway is connected).
    private static final String TEST_CARD_NUMBER = "1111111111111111";
    private static final String TEST_CARD_CVC = "123";
    private static final String TEST_OTP = "111111";

    private static final int MAX_FAILED_PAYMENTS = 5;
    private static final Duration FAILED_PAYMENT_WINDOW = Duration.ofMinutes(10);

    @Value("${payment.test-mode:false}")
    private boolean paymentTestMode;

    /** userId -> [failedCount, windowStartMillis] */
    private final ConcurrentHashMap<Long, long[]> failedPayments = new ConcurrentHashMap<>();

    private final CourseAccessService courseAccess;
    private final EnrollmentRepository enrollmentRepository;
    private final CoursePurchaseRepository purchaseRepository;

    public CoursePaymentService(CourseAccessService courseAccess,
                                EnrollmentRepository enrollmentRepository,
                                CoursePurchaseRepository purchaseRepository) {
        this.courseAccess = courseAccess;
        this.enrollmentRepository = enrollmentRepository;
        this.purchaseRepository = purchaseRepository;
    }

    /** Data for the Payment page's Order Summary. Only a PUBLISHED, PAID course of the caller's own organization. */
    @Transactional(readOnly = true)
    public CoursePaymentDto.CheckoutResponse getCheckout(Long courseId) {
        User user = courseAccess.currentUser();
        CourseEntity course = requirePayableCourse(user, courseId);
        return CoursePaymentDto.CheckoutResponse.builder()
                .courseId(course.getId())
                .courseName(course.getName())
                .description(course.getDescription())
                .thumbnailUrl(course.getThumbnailUrl())
                .price(course.getPrice())
                .alreadyEnrolled(enrollmentRepository.existsByCourseIdAndUserId(courseId, user.getId()))
                .build();
    }

    /** Pay -> save the purchase -> enroll. All-or-nothing: if enrolling fails the purchase row is rolled back too. */
    @Transactional
    public CoursePaymentDto.PurchaseResponse purchase(Long courseId, CoursePaymentDto.PurchaseRequest request) {
        User user = courseAccess.currentUser();
        CourseEntity course = requirePayableCourse(user, courseId);

        if (enrollmentRepository.existsByCourseIdAndUserId(courseId, user.getId())) {
            throw new ApiException("You are already enrolled in this course.", HttpStatus.CONFLICT);
        }

        verifyTestPayment(user.getId(), request);

        LocalDateTime now = LocalDateTime.now();
        purchaseRepository.save(toPurchase(user, course, request.getBilling(), now));

        try {
            enrollmentRepository.saveAndFlush(EnrollmentEntity.builder()
                    .courseId(courseId)
                    .userId(user.getId())
                    .enrolledAt(now)
                    .progressPercent(0)
                    .status(EnrollmentStatus.NOT_STARTED)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new ApiException("You are already enrolled in this course. Please refresh.", HttpStatus.CONFLICT);
        }

        failedPayments.remove(user.getId());
        log.info("Course purchased. userId={}, courseId={}, amount={}", user.getId(), courseId, course.getPrice());

        return CoursePaymentDto.PurchaseResponse.builder()
                .courseId(courseId)
                .courseName(course.getName())
                .amount(course.getPrice())
                .purchasedAt(now)
                .enrollmentStatus(EnrollmentStatus.NOT_STARTED.name())
                .build();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private CourseEntity requirePayableCourse(User user, Long courseId) {
        if (!courseAccess.canViewCourseList(user)) {
            throw new ApiException("You do not have permission to enroll in courses.", HttpStatus.FORBIDDEN);
        }
        CourseEntity course = courseAccess.requireCourseInOrganization(courseId);
        if (!"PUBLISHED".equalsIgnoreCase(course.getStatus())) {
            throw new ApiException("Course not found with id: " + courseId, HttpStatus.NOT_FOUND);
        }
        if (!course.isPaid()) {
            throw new ApiException("This course is free. No payment is needed.", HttpStatus.BAD_REQUEST);
        }
        return course;
    }

    private void verifyTestPayment(Long userId, CoursePaymentDto.PurchaseRequest request) {
        if (!paymentTestMode) {
            // No real gateway connected and test mode is off -> never enroll in a paid course for free.
            throw new ApiException("Online payment is not available right now.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        long now = System.currentTimeMillis();
        long[] state = failedPayments.compute(userId, (k, v) ->
                (v == null || now - v[1] > FAILED_PAYMENT_WINDOW.toMillis()) ? new long[]{0, now} : v);
        if (state[0] >= MAX_FAILED_PAYMENTS) {
            throw new RateLimitExceededException("Too many failed payment attempts. Please try again later.");
        }

        String card = request.getCardNumber().replaceAll("\\s", "");
        boolean ok = constantTimeEquals(card, TEST_CARD_NUMBER)
                & constantTimeEquals(request.getCvc(), TEST_CARD_CVC)
                & constantTimeEquals(request.getOtp(), TEST_OTP);
        if (!ok) {
            state[0]++;
            throw new ApiException("Payment failed. Please check your card details and verification code.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private CoursePurchaseEntity toPurchase(User user, CourseEntity course,
                                            CoursePaymentDto.BillingRequest b, LocalDateTime now) {
        CoursePurchaseEntity p = new CoursePurchaseEntity();
        p.setUserId(user.getId());
        p.setCourseId(course.getId());
        p.setOrganizationId(course.getOrganizationId());
        p.setCourseName(course.getName());
        p.setAmount(course.getPrice());          // price from the DB, never from the request
        p.setBillingName(resolveBillingName(b, user));
        p.setCompanyName(blankToNull(b.getCompanyName()));
        p.setBillingEmail(b.getEmail().trim());
        p.setAddress(b.getAddress().trim());
        p.setCity(b.getCity().trim());
        p.setState(blankToNull(b.getState()));
        p.setPostalCode(b.getPostalCode().trim());
        p.setCountry(b.getCountry().trim());
        p.setPurchasedAt(now);
        return p;
    }

    private String resolveBillingName(CoursePaymentDto.BillingRequest b, User user) {
        String first = b.getFirstName() == null ? "" : b.getFirstName().trim();
        String last = b.getLastName() == null ? "" : b.getLastName().trim();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? (user.getFirstName() + " " + user.getLastName()).trim() : name;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}