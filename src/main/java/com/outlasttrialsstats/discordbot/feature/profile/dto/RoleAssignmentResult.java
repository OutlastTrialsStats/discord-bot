package com.outlasttrialsstats.discordbot.feature.profile.dto;

import java.util.List;

public record RoleAssignmentResult(
        Status status,
        List<String> addedRoles,
        List<String> removedRoles
) {
    public enum Status {
        VERIFIED,
        NOT_VERIFIED,
        LOOKUP_FAILED
    }

    public static RoleAssignmentResult notVerified() {
        return new RoleAssignmentResult(Status.NOT_VERIFIED, List.of(), List.of());
    }

    public static RoleAssignmentResult notVerified(List<String> removedRoles) {
        return new RoleAssignmentResult(Status.NOT_VERIFIED, List.of(), removedRoles);
    }

    public static RoleAssignmentResult lookupFailed() {
        return new RoleAssignmentResult(Status.LOOKUP_FAILED, List.of(), List.of());
    }

    public static RoleAssignmentResult of(List<String> addedRoles, List<String> removedRoles) {
        return new RoleAssignmentResult(Status.VERIFIED, addedRoles, removedRoles);
    }

    public boolean verified() {
        return status == Status.VERIFIED;
    }

    public boolean isLookupFailed() {
        return status == Status.LOOKUP_FAILED;
    }

    public boolean hasChanges() {
        return !addedRoles.isEmpty() || !removedRoles.isEmpty();
    }
}
