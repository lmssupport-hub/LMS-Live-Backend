package com.example.lms.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public class CourseDto {

    // Create / Update Course form body
    public static class CourseRequest {

        @NotBlank(message = "Course Name is required.")
        private String name;

        private String category;

        private Long instructorId;

        private String courseLevel;

        private String thumbnailUrl;

        public CourseRequest() {
        }

        public CourseRequest(String name, String category, Long instructorId, String courseLevel, String thumbnailUrl) {
            this.name = name;
            this.category = category;
            this.instructorId = instructorId;
            this.courseLevel = courseLevel;
            this.thumbnailUrl = thumbnailUrl;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Long getInstructorId() {
            return instructorId;
        }

        public void setInstructorId(Long instructorId) {
            this.instructorId = instructorId;
        }

        public String getCourseLevel() {
            return courseLevel;
        }

        public void setCourseLevel(String courseLevel) {
            this.courseLevel = courseLevel;
        }

        public String getThumbnailUrl() {
            return thumbnailUrl;
        }

        public void setThumbnailUrl(String thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    // Course List / Course Detail response
    public static class CourseResponse {
        private Long id;
        private String name;
        private String category;
        private Long instructorId;
        private String courseLevel;
        private String thumbnailUrl;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public CourseResponse() {
        }

        private CourseResponse(Builder b) {
            this.id = b.id;
            this.name = b.name;
            this.category = b.category;
            this.instructorId = b.instructorId;
            this.courseLevel = b.courseLevel;
            this.thumbnailUrl = b.thumbnailUrl;
            this.createdAt = b.createdAt;
            this.updatedAt = b.updatedAt;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Long getInstructorId() {
            return instructorId;
        }

        public void setInstructorId(Long instructorId) {
            this.instructorId = instructorId;
        }

        public String getCourseLevel() {
            return courseLevel;
        }

        public void setCourseLevel(String courseLevel) {
            this.courseLevel = courseLevel;
        }

        public String getThumbnailUrl() {
            return thumbnailUrl;
        }

        public void setThumbnailUrl(String thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Long id;
            private String name;
            private String category;
            private Long instructorId;
            private String courseLevel;
            private String thumbnailUrl;
            private LocalDateTime createdAt;
            private LocalDateTime updatedAt;

            public Builder id(Long id) {
                this.id = id;
                return this;
            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder category(String category) {
                this.category = category;
                return this;
            }

            public Builder instructorId(Long instructorId) {
                this.instructorId = instructorId;
                return this;
            }

            public Builder courseLevel(String courseLevel) {
                this.courseLevel = courseLevel;
                return this;
            }

            public Builder thumbnailUrl(String thumbnailUrl) {
                this.thumbnailUrl = thumbnailUrl;
                return this;
            }

            public Builder createdAt(LocalDateTime createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public Builder updatedAt(LocalDateTime updatedAt) {
                this.updatedAt = updatedAt;
                return this;
            }

            public CourseResponse build() {
                return new CourseResponse(this);
            }
        }
    }
}
