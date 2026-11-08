package com.outlasttrialsstats.discordbot.feature.communityevent.alert;

import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import com.outlasttrialsstats.discordbot.entity.CommunityAlertType;
import com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEvents;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CommunityAlertEvaluator {

    private CommunityAlertEvaluator() {
    }

    public static Evaluation evaluate(DiscordCommunityEvent event, AlertProgress previous,
                                      Map<CommunityAlertType, AlertRule> rules, Instant now) {
        int reached = CommunityEvents.highestReachedIndex(event);
        if (previous == null) {
            return new Evaluation(new AlertProgress(reached, reached), List.of(), true);
        }

        List<CommunityAlert> alerts = new ArrayList<>();
        int lastReached = previous.lastReachedIndex();
        int preAlerted = previous.preAlertedIndex();

        if (reached > lastReached) {
            if (rule(rules, CommunityAlertType.MILESTONE_REACHED).enabled()) {
                alerts.add(new CommunityAlert(CommunityAlertType.MILESTONE_REACHED, reached));
            }
            lastReached = reached;
            preAlerted = Math.max(preAlerted, reached);
        }

        int next = reached + 1;
        AlertRule preAlert = rule(rules, CommunityAlertType.PRE_ALERT);
        Instant eta = CommunityEvents.instant(event.getEtaNextMilestone());
        if (Boolean.TRUE.equals(event.getActive()) && preAlert.enabled()
                && next < CommunityEvents.milestones(event).size() && next > preAlerted
                && eta != null && !eta.isAfter(now.plus(Duration.ofMinutes(preAlert.leadTimeMinutes())))) {
            alerts.add(new CommunityAlert(CommunityAlertType.PRE_ALERT, next));
            preAlerted = next;
        }

        AlertProgress progress = new AlertProgress(lastReached, preAlerted);
        return new Evaluation(progress, List.copyOf(alerts), !progress.equals(previous));
    }

    private static AlertRule rule(Map<CommunityAlertType, AlertRule> rules, CommunityAlertType type) {
        return rules.getOrDefault(type, AlertRule.defaults(type));
    }

    public record AlertProgress(int lastReachedIndex, int preAlertedIndex) {
    }

    public record CommunityAlert(CommunityAlertType type, int milestoneIndex) {
    }

    public record Evaluation(AlertProgress progress, List<CommunityAlert> alerts, boolean changed) {
    }
}
