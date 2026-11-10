package com.example.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Locale;

/**
 * What the logged-in user is allowed to do, flattened from their role's permission tree.
 * Sent to the Angular app (login response + GET /api/profile/permissions) so the UI can
 * enable only the options the role grants. The server still enforces the same rules.
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

    /** fullAccess = company ADMIN / SUPER_ADMIN (every action); otherwise the role's grants. */
    @Getter
    @AllArgsConstructor
    public static class Snapshot {
        private final boolean fullAccess;
        private final List<Grant> permissions;

        public static Snapshot none() {
            return new Snapshot(false, List.of());
        }

        public static Snapshot full() {
            return new Snapshot(true, List.of());
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
