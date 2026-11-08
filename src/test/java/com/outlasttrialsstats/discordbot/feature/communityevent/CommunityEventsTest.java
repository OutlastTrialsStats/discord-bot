package com.outlasttrialsstats.discordbot.feature.communityevent;

import static com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEventTestData.liveEvent;
import static org.assertj.core.api.Assertions.assertThat;

import com.outlasttrialsstats.backend.api.model.CommunityEventType;
import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommunityEventsTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    @Test
    void selectCurrent_prefersLatestLiveEvent() {
        DiscordCommunityEvent older = liveEvent("older", 0, null)
                .startsAt(Instant.parse("2026-10-01T00:00:00Z").atOffset(ZoneOffset.UTC));
        DiscordCommunityEvent newer = liveEvent("newer", 0, null);

        assertThat(CommunityEvents.selectCurrent(List.of(older, newer), NOW)).contains(newer);
    }

    @Test
    void selectCurrent_ignoresCounters() {
        DiscordCommunityEvent counter = liveEvent("counter", 0, null).type(CommunityEventType.COUNTER);

        assertThat(CommunityEvents.selectCurrent(List.of(counter), NOW)).isEmpty();
    }

    @Test
    void selectCurrent_fallsBackToUpcomingThenLastEnded() {
        DiscordCommunityEvent upcoming = liveEvent("upcoming", 0, null).active(false)
                .startsAt(NOW.plusSeconds(3600).atOffset(ZoneOffset.UTC));
        DiscordCommunityEvent ended = liveEvent("ended", 0, null).active(false)
                .endsAt(NOW.minusSeconds(3600).atOffset(ZoneOffset.UTC));
        DiscordCommunityEvent endedEarlier = liveEvent("earlier", 0, null).active(false)
                .endsAt(NOW.minusSeconds(7200).atOffset(ZoneOffset.UTC));

        assertThat(CommunityEvents.selectCurrent(List.of(ended, upcoming), NOW)).contains(upcoming);
        assertThat(CommunityEvents.selectCurrent(List.of(endedEarlier, ended), NOW)).contains(ended);
    }

    @Test
    void highestReachedIndex() {
        assertThat(CommunityEvents.highestReachedIndex(liveEvent("e", 500, null))).isEqualTo(-1);
        assertThat(CommunityEvents.highestReachedIndex(liveEvent("e", 2_000, null))).isEqualTo(1);
    }

    @Test
    void assetUrl_resolvesGameAssetsAndSitePaths() {
        assertThat(CommunityEvents.assetUrl("item_x")).isEqualTo("https://outlasttrialsstats.com/game-assets/item_x.webp");
        assertThat(CommunityEvents.assetUrl("/images/a.webp")).isEqualTo("https://outlasttrialsstats.com/images/a.webp");
        assertThat(CommunityEvents.assetUrl(null)).isNull();
    }
}
