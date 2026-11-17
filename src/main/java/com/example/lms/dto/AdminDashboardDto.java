package com.example.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class AdminDashboardDto {

    /** One chart series: labels[i] <-> values[i]; total = sum of values. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Series {
        private List<String> labels;
        private List<Double> values;
        private double total;
    }

    /** Head-count per role inside the organization. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDistribution {
        private long admins;
        private long instructors;
        private long learners;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        /** WEEK | MONTH | YEAR */
        private String period;
        private Series logins;
        private Series newSignups;
        /** Paid course enrollments (course_purchases) of the organization. */
        private Series revenue;
        private UserDistribution userDistribution;
        /** 0-100: completed enrollments / all enrollments of the organization's courses. */
        private double courseCompletionRate;
        /** Users of the organization who are logged in right now (login -> online, logout -> offline, no heartbeat for 2 minutes -> offline). */
        private long onlineUsers;
        /** Average time from enrollment to completion, e.g. "2d 4h" (or "0h 0m" when nothing is completed yet). */
        private String averageLearningTime;
    }
}