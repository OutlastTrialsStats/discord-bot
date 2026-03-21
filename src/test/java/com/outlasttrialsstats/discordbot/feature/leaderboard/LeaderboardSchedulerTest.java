package com.outlasttrialsstats.discordbot.feature.leaderboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardSchedulerTest {

    @Mock
    private JDA jda;

    @Mock
    private LeaderboardService leaderboardService;

    @InjectMocks
    private LeaderboardScheduler leaderboardScheduler;

    @Captor
    private ArgumentCaptor<Consumer<Throwable>> errorCallbackCaptor;

    private static final String GUILD_ID = "guild-1";
    private static final String CHANNEL_ID = "channel-1";

    @Test
    void updateLeaderboards_channelNotFound_removesBinding() {
        LeaderboardChannel binding = new LeaderboardChannel(
                GUILD_ID, StatisticType.PRESTIGE, CHANNEL_ID, List.of("msg-1"), 1);
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding));
        when(jda.getTextChannelById(CHANNEL_ID)).thenReturn(null);

        leaderboardScheduler.updateLeaderboards();

        verify(leaderboardService).removeBinding(GUILD_ID, StatisticType.PRESTIGE);
    }

    @Test
    void updateLeaderboards_apiFails_skipsWithoutRemovingBinding() {
        LeaderboardChannel binding = new LeaderboardChannel(
                GUILD_ID, StatisticType.PRESTIGE, CHANNEL_ID, List.of("msg-1"), 1);
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding));

        TextChannel channel = mock(TextChannel.class);
        when(jda.getTextChannelById(CHANNEL_ID)).thenReturn(channel);
        when(leaderboardService.fetchLeaderboard(StatisticType.PRESTIGE, 1)).thenReturn(Optional.empty());

        leaderboardScheduler.updateLeaderboards();

        verify(leaderboardService, never()).removeBinding(any(), any());
        verify(channel, never()).editMessageEmbedsById(any(String.class), any(MessageEmbed.class));
    }

    @Test
    void updateLeaderboards_success_editsMessages() {
        LeaderboardChannel binding = new LeaderboardChannel(
                GUILD_ID, StatisticType.PRESTIGE, CHANNEL_ID, List.of("msg-1", "msg-2"), 2);
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding));

        TextChannel channel = mock(TextChannel.class);
        Guild guild = mock(Guild.class);
        when(jda.getTextChannelById(CHANNEL_ID)).thenReturn(channel);
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);

        DiscordLeaderboardResponse response = new DiscordLeaderboardResponse();
        response.setResults(List.of());
        when(leaderboardService.fetchLeaderboard(eq(StatisticType.PRESTIGE), any(int.class)))
                .thenReturn(Optional.of(response));

        MessageEmbed embedPage1 = mock(MessageEmbed.class);
        MessageEmbed embedPage2 = mock(MessageEmbed.class);
        when(leaderboardService.buildLeaderboardEmbed(eq(GUILD_ID), eq(guild), eq(StatisticType.PRESTIGE), eq(response), eq(true), eq(true), eq(false)))
                .thenReturn(embedPage1);
        when(leaderboardService.buildLeaderboardEmbed(eq(GUILD_ID), eq(guild), eq(StatisticType.PRESTIGE), eq(response), eq(false), eq(true), eq(false)))
                .thenReturn(embedPage2);

        MessageEditAction editAction = mock(MessageEditAction.class);
        when(channel.editMessageEmbedsById(any(String.class), any(MessageEmbed.class))).thenReturn(editAction);

        leaderboardScheduler.updateLeaderboards();

        verify(channel).editMessageEmbedsById("msg-1", embedPage1);
        verify(channel).editMessageEmbedsById("msg-2", embedPage2);
    }

    @Test
    void updateLeaderboards_multipleBindings_processesAll() {
        LeaderboardChannel binding1 = new LeaderboardChannel(
                GUILD_ID, StatisticType.PRESTIGE, CHANNEL_ID, List.of("msg-1"), 1);
        LeaderboardChannel binding2 = new LeaderboardChannel(
                "guild-2", StatisticType.DEATHS, "channel-2", List.of("msg-2"), 1);

        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding1, binding2));
        when(jda.getTextChannelById(CHANNEL_ID)).thenReturn(null);
        when(jda.getTextChannelById("channel-2")).thenReturn(null);

        leaderboardScheduler.updateLeaderboards();

        verify(leaderboardService).removeBinding(GUILD_ID, StatisticType.PRESTIGE);
        verify(leaderboardService).removeBinding("guild-2", StatisticType.DEATHS);
    }

    @Test
    void updateLeaderboards_unknownMessageError_removesBinding() {
        LeaderboardChannel binding = new LeaderboardChannel(
                GUILD_ID, StatisticType.PRESTIGE, CHANNEL_ID, List.of("msg-1"), 1);
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding));

        TextChannel channel = mock(TextChannel.class);
        Guild guild = mock(Guild.class);
        when(jda.getTextChannelById(CHANNEL_ID)).thenReturn(channel);
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);

        DiscordLeaderboardResponse response = new DiscordLeaderboardResponse();
        response.setResults(List.of());
        when(leaderboardService.fetchLeaderboard(StatisticType.PRESTIGE, 1)).thenReturn(Optional.of(response));

        MessageEmbed embed = mock(MessageEmbed.class);
        when(leaderboardService.buildLeaderboardEmbed(eq(GUILD_ID), eq(guild), eq(StatisticType.PRESTIGE), eq(response), eq(true), eq(true), eq(false)))
                .thenReturn(embed);

        MessageEditAction editAction = mock(MessageEditAction.class);
        when(channel.editMessageEmbedsById("msg-1", embed)).thenReturn(editAction);

        leaderboardScheduler.updateLeaderboards();

        verify(editAction).queue(any(), errorCallbackCaptor.capture());

        ErrorResponseException unknownMessageError = mock(ErrorResponseException.class);
        when(unknownMessageError.getErrorResponse()).thenReturn(ErrorResponse.UNKNOWN_MESSAGE);
        errorCallbackCaptor.getValue().accept(unknownMessageError);

        verify(leaderboardService).removeBinding(GUILD_ID, StatisticType.PRESTIGE);
    }

    @Test
    void updateLeaderboards_otherError_doesNotRemoveBinding() {
        LeaderboardChannel binding = new LeaderboardChannel(
                GUILD_ID, StatisticType.PRESTIGE, CHANNEL_ID, List.of("msg-1"), 1);
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding));

        TextChannel channel = mock(TextChannel.class);
        Guild guild = mock(Guild.class);
        when(jda.getTextChannelById(CHANNEL_ID)).thenReturn(channel);
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);

        DiscordLeaderboardResponse response = new DiscordLeaderboardResponse();
        response.setResults(List.of());
        when(leaderboardService.fetchLeaderboard(StatisticType.PRESTIGE, 1)).thenReturn(Optional.of(response));

        MessageEmbed embed = mock(MessageEmbed.class);
        when(leaderboardService.buildLeaderboardEmbed(eq(GUILD_ID), eq(guild), eq(StatisticType.PRESTIGE), eq(response), eq(true), eq(true), eq(false)))
                .thenReturn(embed);

        MessageEditAction editAction = mock(MessageEditAction.class);
        when(channel.editMessageEmbedsById("msg-1", embed)).thenReturn(editAction);

        leaderboardScheduler.updateLeaderboards();

        verify(editAction).queue(any(), errorCallbackCaptor.capture());

        // Simulate a rate limit or other transient error
        RuntimeException transientError = new RuntimeException("rate limited");
        errorCallbackCaptor.getValue().accept(transientError);

        verify(leaderboardService, never()).removeBinding(any(), any());
    }

    @Test
    void updateLeaderboards_noBindings_doesNothing() {
        when(leaderboardService.getAllBindings()).thenReturn(List.of());

        leaderboardScheduler.updateLeaderboards();

        verify(jda, never()).getTextChannelById(any());
    }
}
