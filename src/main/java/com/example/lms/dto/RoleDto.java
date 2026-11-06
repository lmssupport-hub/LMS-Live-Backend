package com.example.lms.dto;

import com.example.lms.dto.PackageDto.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

public class RoleDto {

    @Getter
    @NoArgsConstructor
    public static class Request {

        @NotBlank(message = "Role Name is required")
        @Size(min = 4, max = 50, message = "Role name must be between 4 and 50 characters")
        @Pattern(regexp = "^[A-Za-z ]+$", message = "Role Name can contain letters and spaces only")
        private String name;

        @Setter
        private String description;

        // SRS: "Save button remains disabled until ... at least one permission
        // is selected." @NotEmpty rejects both null AND an empty list.
        @Setter
        @NotEmpty(message = "Please configure at least one permission before saving")
        private List<Category> permissions;

        /** Trim here so validation and duplicate checks always see the clean value. */
        public void setName(String name) {
            this.name = name == null ? null : name.trim();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Response {
        private Long id;
        private String name;
        private String description;
        private List<Category> permissions;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // Used for the "toggle Active/Inactive" action from the role list screen
    @Getter
    @Setter
    @NoArgsConstructor
    public static class StatusUpdateRequest {
        @NotBlank(message = "Status is required")
        private String status; // Active / Inactive
    }
}