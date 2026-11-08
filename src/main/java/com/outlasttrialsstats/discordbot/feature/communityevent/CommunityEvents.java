package com.outlasttrialsstats.discordbot.feature.communityevent;

import com.outlasttrialsstats.backend.api.model.CommunityEventArt;
import com.outlasttrialsstats.backend.api.model.CommunityEventMilestone;
import com.outlasttrialsstats.backend.api.model.CommunityEventType;
import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import com.outlasttrialsstats.discordbot.shared.SiteLinks;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class CommunityEvents {

    public enum Status { LIVE, UPCOMING, ENDED }

    private static final CommunityEventArt NO_ART = new CommunityEventArt();

    private CommunityEvents() {
    }

    public static Optional<DiscordCommunityEvent> find(List<DiscordCommunityEvent> events, String eventId) {
        return events.stream().filter(event -> event.getId().equals(eventId)).findFirst();
    }

    public static Optional<DiscordCommunityEvent> selectCurrent(List<DiscordCommunityEvent> events, Instant now) {
        List<DiscordCommunityEvent> timed = events.stream()
                .filter(event -> event.getType() == CommunityEventType.EVENT)
                .toList();
        return latest(timed, event -> status(event, now) == Status.LIVE,
                        event -> Objects.requireNonNullElse(instant(event.getStartsAt()), Instant.MIN))
                .or(() -> timed.stream()
                        .filter(event -> status(event, now) == Status.UPCOMING)
                        .min(Comparator.comparing(event -> instant(event.getStartsAt()))))
                .or(() -> latest(timed, event -> event.getEndsAt() != null, event -> instant(event.getEndsAt())));
    }

    private static Optional<DiscordCommunityEvent> latest(List<DiscordCommunityEvent> events,
                                                          Predicate<DiscordCommunityEvent> filter,
                                                          Function<DiscordCommunityEvent, Instant> key) {
        return events.stream().filter(filter).max(Comparator.comparing(key));
    }

    public static Status status(DiscordCommunityEvent event, Instant now) {
        if (Boolean.TRUE.equals(event.getActive())) {
            return Status.LIVE;
        }
        Instant startsAt = instant(event.getStartsAt());
        return startsAt != null && startsAt.isAfter(now) ? Status.UPCOMING : Status.ENDED;
    }

    public static int highestReachedIndex(DiscordCommunityEvent event) {
        int highest = -1;
        for (CommunityEventMilestone milestone : milestones(event)) {
            if (Boolean.TRUE.equals(milestone.getReached()) && milestone.getIndex() != null) {
                highest = Math.max(highest, milestone.getIndex());
            }
        }
        return highest;
    }

    public static List<CommunityEventMilestone> milestones(DiscordCommunityEvent event) {
        return event.getMilestones() == null ? List.of() : event.getMilestones();
    }

    public static double currentValue(DiscordCommunityEvent event) {
        return event.getCurrentValue() == null ? 0 : event.getCurrentValue();
    }

    public static CommunityEventArt art(DiscordCommunityEvent event) {
        return event.getArt() == null ? NO_ART : event.getArt();
    }

    public static String eventUrl(DiscordCommunityEvent event) {
        return SiteLinks.SITE_URL + "/events/" + event.getId();
    }

    public static String assetUrl(String asset) {
        if (asset == null || asset.isBlank()) {
            return null;
        }
        if (asset.startsWith("/")) {
            return SiteLinks.SITE_URL + asset;
        }
        return SiteLinks.SITE_URL + "/game-assets/" + asset + ".webp";
    }

    public static Instant instant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
