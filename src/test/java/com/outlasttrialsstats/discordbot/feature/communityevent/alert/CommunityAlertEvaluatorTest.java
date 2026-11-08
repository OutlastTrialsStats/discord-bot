package com.outlasttrialsstats.discordbot.feature.communityevent.alert;

import static com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEventTestData.liveEvent;
import static org.assertj.core.api.Assertions.assertThat;

import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import com.outlasttrialsstats.discordbot.entity.CommunityAlertType;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.CommunityAlertEvaluator.AlertProgress;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.CommunityAlertEvaluator.CommunityAlert;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.CommunityAlertEvaluator.Evaluation;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommunityAlertEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");
    private static final Map<CommunityAlertType, AlertRule> DEFAULTS = Map.of();

    @Test
    void firstObservation_recordsProgressWithoutAlerts() {
        Evaluation evaluation = CommunityAlertEvaluator.evaluate(liveEvent("e", 2_500, NOW.plusSeconds(60)), null,
                DEFAULTS, NOW);

        assertThat(evaluation.alerts()).isEmpty();
        assertThat(evaluation.progress()).isEqualTo(new AlertProgress(1, 1));
        assertThat(evaluation.changed()).isTrue();
    }

    @Test
    void newPhase_sendsMilestoneAlertForHighestReachedOnly() {
        Evaluation evaluation = CommunityAlertEvaluator.evaluate(liveEvent("e", 3_100, null),
                new AlertProgress(0, 0), DEFAULTS, NOW);

        assertThat(evaluation.alerts())
                .containsExactly(new CommunityAlert(CommunityAlertType.MILESTONE_REACHED, 2));
        assertThat(evaluation.progress()).isEqualTo(new AlertProgress(2, 2));
    }

    @Test
    void unchangedProgress_sendsNothingAndNeedsNoSave() {
        Evaluation evaluation = CommunityAlertEvaluator.evaluate(liveEvent("e", 1_500, null),
                new AlertProgress(0, 0), DEFAULTS, NOW);

        assertThat(evaluation.alerts()).isEmpty();
        assertThat(evaluation.changed()).isFalse();
    }

    @Test
    void etaWithinLeadTime_sendsPreAlertOnce() {
        DiscordCommunityEvent event = liveEvent("e", 1_900, NOW.plus(Duration.ofMinutes(30)));

        Evaluation first = CommunityAlertEvaluator.evaluate(event, new AlertProgress(0, 0), DEFAULTS, NOW);
        Evaluation second = CommunityAlertEvaluator.evaluate(event, first.progress(), DEFAULTS, NOW.plusSeconds(60));

        assertThat(first.alerts()).containsExactly(new CommunityAlert(CommunityAlertType.PRE_ALERT, 1));
        assertThat(first.progress()).isEqualTo(new AlertProgress(0, 1));
        assertThat(second.alerts()).isEmpty();
    }

    @Test
    void fluctuatingEta_doesNotRepeatPreAlert() {
        AlertProgress afterPreAlert = new AlertProgress(0, 1);

        Evaluation later = CommunityAlertEvaluator.evaluate(liveEvent("e", 1_900, NOW.plus(Duration.ofHours(5))),
                afterPreAlert, DEFAULTS, NOW);
        Evaluation back = CommunityAlertEvaluator.evaluate(liveEvent("e", 1_950, NOW.plus(Duration.ofMinutes(10))),
                later.progress(), DEFAULTS, NOW);

        assertThat(later.alerts()).isEmpty();
        assertThat(back.alerts()).isEmpty();
    }

    @Test
    void etaBeyondLeadTime_sendsNoPreAlert() {
        Evaluation evaluation = CommunityAlertEvaluator.evaluate(liveEvent("e", 1_100, NOW.plus(Duration.ofHours(2))),
                new AlertProgress(0, 0), DEFAULTS, NOW);

        assertThat(evaluation.alerts()).isEmpty();
    }

    @Test
    void configuredLeadTime_isUsed() {
        Map<CommunityAlertType, AlertRule> rules = Map.of(CommunityAlertType.PRE_ALERT,
                new AlertRule(CommunityAlertType.PRE_ALERT, true, null, 180));

        Evaluation evaluation = CommunityAlertEvaluator.evaluate(liveEvent("e", 1_100, NOW.plus(Duration.ofHours(2))),
                new AlertProgress(0, 0), rules, NOW);

        assertThat(evaluation.alerts()).containsExactly(new CommunityAlert(CommunityAlertType.PRE_ALERT, 1));
    }

    @Test
    void disabledTypes_sendNothingButMilestoneProgressAdvances() {
        Map<CommunityAlertType, AlertRule> rules = Map.of(
                CommunityAlertType.MILESTONE_REACHED, new AlertRule(CommunityAlertType.MILESTONE_REACHED, false, null, null),
                CommunityAlertType.PRE_ALERT, new AlertRule(CommunityAlertType.PRE_ALERT, false, null, 60));

        Evaluation evaluation = CommunityAlertEvaluator.evaluate(liveEvent("e", 2_100, NOW.plusSeconds(60)),
                new AlertProgress(0, 0), rules, NOW);

        assertThat(evaluation.alerts()).isEmpty();
        assertThat(evaluation.progress()).isEqualTo(new AlertProgress(1, 1));
    }

    @Test
    void reachedAndNextPhaseClose_sendsBoth() {
        Evaluation evaluation = CommunityAlertEvaluator.evaluate(liveEvent("e", 2_990, NOW.plusSeconds(120)),
                new AlertProgress(0, 0), DEFAULTS, NOW);

        assertThat(evaluation.alerts()).containsExactly(
                new CommunityAlert(CommunityAlertType.MILESTONE_REACHED, 1),
                new CommunityAlert(CommunityAlertType.PRE_ALERT, 2));
    }

    @Test
    void lastPhaseReached_hasNoPreAlert() {
        Evaluation evaluation = CommunityAlertEvaluator.evaluate(liveEvent("e", 4_100, NOW.plusSeconds(60)),
                new AlertProgress(3, 3), DEFAULTS, NOW);

        assertThat(evaluation.alerts()).isEmpty();
    }

    @Test
    void justEndedEvent_stillAnnouncesLastPhaseButNoPreAlert() {
        DiscordCommunityEvent ended = liveEvent("e", 3_100, NOW.plusSeconds(60)).active(false);

        Evaluation evaluation = CommunityAlertEvaluator.evaluate(ended, new AlertProgress(0, 0), DEFAULTS, NOW);

        assertThat(evaluation.alerts())
                .containsExactly(new CommunityAlert(CommunityAlertType.MILESTONE_REACHED, 2));
        assertThat(evaluation.progress()).isEqualTo(new AlertProgress(2, 2));
    }
}
