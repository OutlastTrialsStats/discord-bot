package com.outlasttrialsstats.discordbot.feature.profile.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoleAssignmentResultTest {

    @Test
    void notVerified_returnsFalseVerified() {
        var result = RoleAssignmentResult.notVerified();

        assertThat(result.verified()).isFalse();
        assertThat(result.addedRoles()).isEmpty();
        assertThat(result.removedRoles()).isEmpty();
    }

    @Test
    void of_withRoles_returnsVerifiedTrue() {
        var result = RoleAssignmentResult.of(List.of("Role1"), List.of("Role2"));

        assertThat(result.verified()).isTrue();
        assertThat(result.addedRoles()).containsExactly("Role1");
        assertThat(result.removedRoles()).containsExactly("Role2");
    }

    @Test
    void of_withEmptyLists_returnsVerifiedTrueAndNoChanges() {
        var result = RoleAssignmentResult.of(List.of(), List.of());

        assertThat(result.verified()).isTrue();
        assertThat(result.hasChanges()).isFalse();
    }

    @Test
    void hasChanges_withAddedRolesOnly_returnsTrue() {
        var result = RoleAssignmentResult.of(List.of("Role1"), List.of());
        assertThat(result.hasChanges()).isTrue();
    }

    @Test
    void hasChanges_withRemovedRolesOnly_returnsTrue() {
        var result = RoleAssignmentResult.of(List.of(), List.of("Role1"));
        assertThat(result.hasChanges()).isTrue();
    }

    @Test
    void hasChanges_withBothAddedAndRemoved_returnsTrue() {
        var result = RoleAssignmentResult.of(List.of("Added"), List.of("Removed"));
        assertThat(result.hasChanges()).isTrue();
    }
}
