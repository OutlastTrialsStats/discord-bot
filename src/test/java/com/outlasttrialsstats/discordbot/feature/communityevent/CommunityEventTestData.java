package com.outlasttrialsstats.discordbot.feature.communityevent;

import com.outlasttrialsstats.backend.api.model.CommunityEventArt;
import com.outlasttrialsstats.backend.api.model.CommunityEventMilestone;
import com.outlasttrialsstats.backend.api.model.CommunityEventType;
import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class CommunityEventTestData {

    public static final long[] AMOUNTS = {1_000, 2_000, 3_000, 4_000};

    private CommunityEventTestData() {
    }

    public static DiscordCommunityEvent liveEvent(String id, double value, Instant eta) {
        List<CommunityEventMilestone> milestones = new ArrayList<>();
        for (int i = 0; i < AMOUNTS.length; i++) {
            milestones.add(new CommunityEventMilestone()
                    .index(i)
                    .amount(AMOUNTS[i])
                    .reached(value >= AMOUNTS[i]));
        }
        return new DiscordCommunityEvent()
                .id(id)
                .name("Event " + id)
                .type(CommunityEventType.EVENT)
                .active(true)
                .startsAt(Instant.parse("2026-11-01T00:00:00Z").atOffset(ZoneOffset.UTC))
                .currentValue(value)
                .sampledAt(Instant.parse("2026-11-08T12:00:00Z").atOffset(ZoneOffset.UTC))
                .milestones(milestones)
                .etaNextMilestone(eta == null ? null : eta.atOffset(ZoneOffset.UTC))
                .ratePerHour(100.0)
                .art(new CommunityEventArt().icon("icon").banner("banner"));
    }
}
