package com.example.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class PackageRevenueDto {

    /** Revenue of ONE day: sum of the prices of every package assigned on that date. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        /** yyyy-MM-dd (server date of the assignment). */
        private String date;
        private Double amount;
        /** How many packages were assigned that day. */
        private Long count;
    }
}