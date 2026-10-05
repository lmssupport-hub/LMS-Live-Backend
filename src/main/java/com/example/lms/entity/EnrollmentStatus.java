package com.example.lms.entity;


public enum EnrollmentStatus {
    NOT_STARTED("Not Started"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed");

    private final String label;

    EnrollmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}