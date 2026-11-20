package com.outlasttrialsstats.discordbot.feature.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.outlasttrialsstats.backend.api.model.StatisticType;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import org.junit.jupiter.api.Test;

class LeaderboardCategoriesTest {

    @Test
    void parsesKnownValuesCaseInsensitively() {
        assertThat(LeaderboardCategories.parse("prestige")).contains(StatisticType.PRESTIGE);
        assertThat(LeaderboardCategories.parse(" EXTRACTED_INTEL ")).contains(StatisticType.EXTRACTED_INTEL);
    }

    @Test
    void mapsTheRenamedEscalationPeak() {
        assertThat(LeaderboardCategories.parse("ESCALATION_PEAK"))
                .contains(StatisticType.ESCALATION_HIGHEST_TRIAL_REACHED);
    }

    @Test
    void rejectsUnknownValues() {
        assertThat(LeaderboardCategories.parse("Escalation")).isEmpty();
        assertThat(LeaderboardCategories.parse(null)).isEmpty();
    }

    @Test
    void suggestsAtMostTheDiscordLimit() {
        assertThat(StatisticType.values().length).isGreaterThan(LeaderboardCategories.MAX_CHOICES);
        assertThat(LeaderboardCategories.suggest("", StatisticType::getValue))
                .hasSize(LeaderboardCategories.MAX_CHOICES);
    }

    @Test
    void filtersByDisplayNameAndValue() {
        assertThat(LeaderboardCategories.suggest("time played", type -> type == StatisticType.ESCALATION_TIME_PLAYED
                        ? "Escalation Time Played" : type.getValue()))
                .extracting(Choice::getAsString)
                .containsExactly("ESCALATION_TIME_PLAYED");
        assertThat(LeaderboardCategories.suggest("escalation_runs", type -> "x"))
                .extracting(Choice::getAsString)
                .containsExactly("ESCALATION_RUNS_COMPLETED");
    }

    @Test
    void formatsPlainCountsWithGrouping() {
        assertThat(LeaderboardCategories.formatValue(StatisticType.PRESTIGE, 1234567)).isEqualTo("1,234,567");
    }

    @Test
    void formatsTimePlayedAsHoursAndMinutes() {
        assertThat(LeaderboardCategories.formatValue(StatisticType.ESCALATION_TIME_PLAYED, 2_364_724))
                .isEqualTo("656h 52m");
        assertThat(LeaderboardCategories.formatValue(StatisticType.ESCALATION_TIME_PLAYED, 125)).isEqualTo("2m 5s");
        assertThat(LeaderboardCategories.formatValue(StatisticType.ESCALATION_TIME_PLAYED, 0)).isEqualTo("-");
    }

    @Test
    void decodesTheEscalationRunScore() {
        long score = 3400L * 4_194_304 + (1_048_576 - 754);

        assertThat(LeaderboardCategories.formatValue(StatisticType.ESCALATION_HIGHEST_TRIAL_REACHED_WITH_TIME, score))
                .isEqualTo("3400 · 12m 34s");
        assertThat(LeaderboardCategories.formatValue(StatisticType.ESCALATION_HIGHEST_TRIAL_REACHED_WITH_TIME, 0))
                .isEqualTo("-");
    }
}
