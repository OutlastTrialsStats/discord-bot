package com.outlasttrialsstats.discordbot.feature.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.outlasttrialsstats.backend.api.model.InvasionRanking;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RoleTargetTest {

    @Test
    void threshold_parsesPositiveNumbers() {
        RoleTarget target = RoleTarget.parse(RoleCategory.LEVEL, " 25 ").orElseThrow();

        assertThat(target.ranked()).isTrue();
        assertThat(target.rank()).isEqualTo(25);
        assertThat(target.label()).isEqualTo("Level 25+");
        assertThat(target.successKey()).isEqualTo("setup.level_role.success");
    }

    @Test
    void threshold_rejectsInvalidInput() {
        assertThat(RoleTarget.parse(RoleCategory.PRESTIGE, "0")).isEmpty();
        assertThat(RoleTarget.parse(RoleCategory.PRESTIGE, "-5")).isEmpty();
        assertThat(RoleTarget.parse(RoleCategory.PRESTIGE, "ten")).isEmpty();
        assertThat(RoleTarget.parse(RoleCategory.PRESTIGE, null)).isEmpty();
    }

    @Test
    void invasionRanking_isStoredByOrdinal() {
        RoleTarget gold = RoleTarget.parse(RoleCategory.INVASION_RANKING, "GOLD_1").orElseThrow();

        assertThat(gold.ranked()).isTrue();
        assertThat(gold.rank()).isEqualTo(InvasionRanking.GOLD_1.ordinal());
        assertThat(gold.label()).isEqualTo("Invasion: Gold I");
        assertThat(RoleTarget.fromRanked(RoleCategory.INVASION_RANKING, InvasionRanking.GOLD_1.ordinal()))
                .contains(gold);
    }

    @Test
    void skill_usesEnumStorageAndDisplayName() {
        RoleTarget target = RoleTarget.parse(RoleCategory.REAGENT_RIG, "DOOR_BLOCKER").orElseThrow();

        assertThat(target.ranked()).isFalse();
        assertThat(target.enumValue()).isEqualTo("DOOR_BLOCKER");
        assertThat(target.label()).isEqualTo("Barricade");
    }

    @Test
    void connectedAccount_hasSingleChoice() {
        assertThat(RoleTarget.choices(RoleCategory.CONNECTED_ACCOUNT)).singleElement()
                .satisfies(target -> assertThat(target.enumValue()).isEqualTo(RoleConfig.CONNECTED_ACCOUNT_VALUE));
    }

    @ParameterizedTest
    @EnumSource(RoleCategory.class)
    void everyCategoryHasEitherThresholdsOrChoicesThatFitASelectMenu(RoleCategory category) {
        if (RoleTarget.usesThreshold(category)) {
            assertThat(RoleTarget.choices(category)).isEmpty();
        } else {
            assertThat(RoleTarget.choices(category)).isNotEmpty().hasSizeLessThanOrEqualTo(25);
        }
    }

    @ParameterizedTest
    @EnumSource(RoleCategory.class)
    void choicesRoundTripThroughTheirKey(RoleCategory category) {
        for (RoleTarget choice : RoleTarget.choices(category)) {
            assertThat(RoleTarget.parse(category, choice.key())).contains(choice);
        }
    }
}
