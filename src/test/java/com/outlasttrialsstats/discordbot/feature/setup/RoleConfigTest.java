package com.outlasttrialsstats.discordbot.feature.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.outlasttrialsstats.backend.api.model.AccountCreationType;
import com.outlasttrialsstats.backend.api.model.ActiveReagentSkillType;
import com.outlasttrialsstats.backend.api.model.InvasionRanking;
import com.outlasttrialsstats.backend.api.model.PlatformType;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RoleConfigTest {

    @Test
    void prestigeThresholds_areSortedAscending() {
        assertThat(RoleConfig.PRESTIGE_THRESHOLDS).isSorted();
    }

    @Test
    void levelThresholds_areSortedAscending() {
        assertThat(RoleConfig.LEVEL_THRESHOLDS).isSorted();
    }

    @Test
    void totalInvasionMatchesThresholds_areSortedAscending() {
        assertThat(RoleConfig.TOTAL_INVASION_MATCHES_THRESHOLDS).isSorted();
    }

    @Test
    void skillNames_coversAllUsedTypes() {
        for (ActiveReagentSkillType skill : ActiveReagentSkillType.values()) {
            assertThat(RoleConfig.SKILL_NAMES).containsKey(skill);
        }
    }

    @Test
    void skillColors_matchesSkillNamesKeys() {
        assertThat(RoleConfig.SKILL_COLORS.keySet())
                .containsExactlyInAnyOrderElementsOf(RoleConfig.SKILL_NAMES.keySet());
    }

    @Test
    void invasionRankingNames_coversAllValues() {
        for (InvasionRanking ranking : InvasionRanking.values()) {
            assertThat(RoleConfig.INVASION_RANKING_NAMES).containsKey(ranking);
        }
    }

    @Test
    void invasionRankingColors_matchesInvasionRankingNamesKeys() {
        assertThat(RoleConfig.INVASION_RANKING_COLORS.keySet())
                .containsExactlyInAnyOrderElementsOf(RoleConfig.INVASION_RANKING_NAMES.keySet());
    }

    @Test
    void platformNames_coversAllValues() {
        for (PlatformType platform : PlatformType.values()) {
            assertThat(RoleConfig.PLATFORM_NAMES).containsKey(platform);
        }
    }

    @Test
    void platformColors_matchesPlatformNamesKeys() {
        assertThat(RoleConfig.PLATFORM_COLORS.keySet())
                .containsExactlyInAnyOrderElementsOf(RoleConfig.PLATFORM_NAMES.keySet());
    }

    private static final Set<AccountCreationType> IGNORED_ACCOUNT_TYPES = Set.of(
            AccountCreationType.NORMAL_USER
    );

    @Test
    void accountTypeNames_andIgnoredAccountTypes_coverAllEnumValues() {
        Set<AccountCreationType> covered = Stream.concat(
                RoleConfig.ACCOUNT_TYPE_NAMES.keySet().stream(),
                IGNORED_ACCOUNT_TYPES.stream()
        ).collect(Collectors.toSet());

        assertThat(covered).containsExactlyInAnyOrder(AccountCreationType.values());
    }

    @Test
    void skillName_returnsCorrectMapping() {
        assertThat(RoleConfig.skillName(ActiveReagentSkillType.STUN)).isEqualTo("Stun");
        assertThat(RoleConfig.skillName(ActiveReagentSkillType.HEAL)).isEqualTo("Heal");
    }

    @Test
    void invasionRankingOrdinal_returnsOrdinal() {
        assertThat(RoleConfig.invasionRankingOrdinal(InvasionRanking.UNRANKED)).isEqualTo(0);
        assertThat(RoleConfig.invasionRankingOrdinal(InvasionRanking.GOLD_1))
                .isEqualTo(InvasionRanking.GOLD_1.ordinal());
    }
}
