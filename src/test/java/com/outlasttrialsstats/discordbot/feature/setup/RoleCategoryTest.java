package com.outlasttrialsstats.discordbot.feature.setup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RoleCategoryTest {

    @ParameterizedTest
    @EnumSource(RoleCategory.class)
    void fromId_withValidId_returnsCorrectCategory(RoleCategory category) {
        assertThat(RoleCategory.fromId(category.getId())).isEqualTo(category);
    }

    @Test
    void fromId_withInvalidId_returnsNull() {
        assertThat(RoleCategory.fromId("nonexistent")).isNull();
    }

    @Test
    void fromId_withNull_returnsNull() {
        assertThat(RoleCategory.fromId(null)).isNull();
    }

    @Test
    void getId_returnsExpectedValues() {
        assertThat(RoleCategory.PRESTIGE.getId()).isEqualTo("prestige");
        assertThat(RoleCategory.LEVEL.getId()).isEqualTo("level");
        assertThat(RoleCategory.TOTAL_INVASION_MATCHES.getId()).isEqualTo("total_invasion_matches");
    }

    @Test
    void getDisplayName_returnsExpectedValues() {
        assertThat(RoleCategory.PRESTIGE.getDisplayName()).isEqualTo("Prestige Roles");
        assertThat(RoleCategory.LEVEL.getDisplayName()).isEqualTo("Level Roles");
        assertThat(RoleCategory.TOTAL_INVASION_MATCHES.getDisplayName()).isEqualTo("Total Invasion Matches Roles");
    }
}
