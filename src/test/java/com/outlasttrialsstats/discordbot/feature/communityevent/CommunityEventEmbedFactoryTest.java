package com.outlasttrialsstats.discordbot.feature.communityevent;

import static com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEventTestData.liveEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.entity.CommunityAlertType;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.CommunityAlertEvaluator.CommunityAlert;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommunityEventEmbedFactoryTest {

    private static final String GUILD_ID = "guild-1";
    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    @Mock
    private MessageService messageService;

    private CommunityEventEmbedFactory factory;

    @BeforeEach
    void setUp() {
        factory = new CommunityEventEmbedFactory(messageService);
        lenient().when(messageService.getLocale(GUILD_ID)).thenReturn(Locale.ENGLISH);
        lenient().when(messageService.getMessage(eq(GUILD_ID), anyString(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    Object[] all = invocation.getArguments();
                    String args = Arrays.stream(all, 2, all.length).map(String::valueOf)
                            .collect(Collectors.joining("|"));
                    return invocation.getArgument(1) + "[" + args + "]";
                });
        lenient().when(messageService.getMessage(eq(GUILD_ID), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1) + "[]");
    }

    @Test
    void progressBar_fillsProportionally() {
        assertThat(CommunityEventEmbedFactory.progressBar(0)).isEqualTo("▱".repeat(12));
        assertThat(CommunityEventEmbedFactory.progressBar(0.5)).isEqualTo("▰".repeat(6) + "▱".repeat(6));
        assertThat(CommunityEventEmbedFactory.progressBar(1.5)).isEqualTo("▰".repeat(12));
    }

    @Test
    void forecast_extrapolatesFromNextEta() {
        Instant eta = Instant.parse("2026-11-08T13:00:00Z");

        assertThat(CommunityEventEmbedFactory.forecast(eta, 2_000, 3_000, 500.0))
                .isEqualTo(Instant.parse("2026-11-08T15:00:00Z"));
        assertThat(CommunityEventEmbedFactory.forecast(eta, 2_000, 3_000, null)).isNull();
        assertThat(CommunityEventEmbedFactory.forecast(null, 2_000, 3_000, 500.0)).isNull();
    }

    @Test
    void progressEmbed_showsPhasesWithEtaAndForecast() {
        Instant eta = Instant.parse("2026-11-08T13:00:00Z");

        MessageEmbed embed = factory.buildProgressEmbed(GUILD_ID, liveEvent("e", 1_500, eta), NOW);

        String description = embed.getDescription();
        assertThat(embed.getTitle()).isEqualTo("Event e");
        assertThat(embed.getUrl()).isEqualTo("https://outlasttrialsstats.com/events/e");
        assertThat(embed.getImage().getUrl()).isEqualTo("https://outlasttrialsstats.com/game-assets/banner.webp");
        assertThat(description)
                .contains("community_event.status.live[]")
                .contains("community_event.total[1,500]")
                .contains("community_event.next[2|2,000]")
                .contains("50.0|500]")
                .contains("community_event.phase.reached_unknown[1|1,000]")
                .contains("community_event.phase.current[2|2,000|<t:" + eta.getEpochSecond() + ":R>]")
                .contains("community_event.phase.forecast[3|3,000|<t:"
                        + eta.plusSeconds(10 * 3600).getEpochSecond() + ":R>]")
                .contains("community_event.phase.forecast[4|4,000|");
    }

    @Test
    void progressEmbed_allPhasesReached() {
        MessageEmbed embed = factory.buildProgressEmbed(GUILD_ID, liveEvent("e", 5_000, null), NOW);

        assertThat(embed.getDescription()).contains("community_event.all_reached[]")
                .doesNotContain("community_event.next");
    }

    @Test
    void alertEmbed_milestoneReached() {
        MessageEmbed embed = factory.buildAlertEmbed(GUILD_ID, liveEvent("e", 2_100, null),
                new CommunityAlert(CommunityAlertType.MILESTONE_REACHED, 1));

        assertThat(embed.getTitle()).isEqualTo("community_event.alert.milestone.title[2]");
        assertThat(embed.getDescription()).isEqualTo("community_event.alert.milestone.description[Event e|2,000]");
    }

    @Test
    void alertEmbed_preAlertShowsRemainingAndEta() {
        Instant eta = Instant.parse("2026-11-08T12:30:00Z");

        MessageEmbed embed = factory.buildAlertEmbed(GUILD_ID, liveEvent("e", 1_900, eta),
                new CommunityAlert(CommunityAlertType.PRE_ALERT, 1));

        assertThat(embed.getTitle()).isEqualTo("community_event.alert.pre.title[2]");
        assertThat(embed.getDescription()).isEqualTo("community_event.alert.pre.description[Event e|100|2,000|<t:"
                + eta.getEpochSecond() + ":R>]");
    }
}
