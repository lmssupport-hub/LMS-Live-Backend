package com.example.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Locale;

/**
 * What the logged-in user is allowed to do, flattened from their role's permission tree.
 * Sent to the Angular app (login response + GET /api/profile/permissions) so the UI can
 * show only the menus / buttons the user is allowed to use. The server still enforces the
 * same rules on every API call.
 */
public class PermissionDto {

    private PermissionDto() {
    }

    /** One feature (e.g. COURSE_MGMT_COURSE_LIST) with its four action flags. */
    @Getter
    @AllArgsConstructor
    public static class Grant {
        private final String featureId;
        private final boolean create;
        private final boolean read;
        private final boolean update;
        private final boolean delete;

        public boolean allows(String action) {
            return switch (action == null ? "" : action.toLowerCase(Locale.ROOT)) {
                case "create" -> create;
                case "read" -> read;
                case "update" -> update;
                case "delete" -> delete;
                default -> false;
            };
        }
    }

    /**
     * fullAccess   = SUPER_ADMIN (app owner) only: every action, nothing to look up.
     * companyAdmin = the company's own ADMIN (self-signup). Always sees Dashboard,
     *                Role Management and Subscription; every other menu comes from the
     *                package ({@link #permissions} = what the package allows).
     * Everyone else (Instructor, Learner, any custom role) gets the role's grants limited
     * to what the company's package allows.
     */
    @Getter
    @AllArgsConstructor
    public static class Snapshot {
        private final boolean fullAccess;
        private final boolean companyAdmin;
        private final List<Grant> permissions;

        public static Snapshot none() {
            return new Snapshot(false, false, List.of());
        }

        public static Snapshot full() {
            return new Snapshot(true, false, List.of());
        }

        public boolean allows(String featureId, String action) {
            if (fullAccess) {
                return true;
            }
            return permissions.stream()
                    .anyMatch(g -> g.getFeatureId().equals(featureId) && g.allows(action));
        }
    }
}
