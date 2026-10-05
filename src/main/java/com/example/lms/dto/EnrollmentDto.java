package com.example.lms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class EnrollmentDto {

    /**
     * Body for both "Enroll Selected User(s)" and "Unenroll".
     *
     * SRS Enroll User Form:
     *  - User is mandatory                       -> "User is required."          (field missing / null)
     *  - Enroll Selected User(s) with none picked -> "Please select at least one user" (empty list)
     *
     * The upper bound (200) is enforced in the service so it can carry its own message.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UserIdsRequest {

        @NotNull(message = "User is required.")
        @Size(min = 1, message = "Please select at least one user")
        private List<@NotNull(message = "User is required.") Long> userIds;
    }

    /** One row of the Enrolled Course List (SRS Field List, Enrolled Course List #1-#8). */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EnrolledLearnerResponse {
        private Long enrollmentId;
        private Long userId;
        private String userName;            // #1 User
        private LocalDate enrollmentDate;   // #2
        private LocalDate completionDate;   // #3 (null until completed)
        private LocalDate expirationDate;   // #4 (null when not applicable)
        private Integer progressPercent;    // #5 0-100
        private String status;              // #6 Not Started | In Progress | Completed
        private BigDecimal scorePercent;    // #7 0-100 (null when no score)
        private String completion;          // #8
    }

    /** One option of the Enroll User Form's multi-select "User" dropdown. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EligibleUserResponse {
        private Long id;
        private String name;
        private String email;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EnrollmentActionResponse {
        private Long courseId;
        private int affectedCount;
    }
}