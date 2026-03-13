package com.outlasttrialsstats.discordbot.feature.profile.dto;

import java.util.List;

public record RoleAssignmentResult(
        boolean verified,
        List<String> addedRoles,
        List<String> removedRoles
) {
    public static RoleAssignmentResult notVerified() {
        return new RoleAssignmentResult(false, List.of(), List.of());
    }

    public static RoleAssignmentResult of(List<String> addedRoles, List<String> removedRoles) {
        return new RoleAssignmentResult(true, addedRoles, removedRoles);
    }

    public boolean hasChanges() {
        return !addedRoles.isEmpty() || !removedRoles.isEmpty();
    }
}
