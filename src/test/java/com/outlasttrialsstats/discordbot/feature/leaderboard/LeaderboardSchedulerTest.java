package com.outlasttrialsstats.discordbot.feature.leaderboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardBinding;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardSchedulerTest {

    private static final String GUILD_ID = "guild-1";
    private static final String CHANNEL_ID = "channel-1";
    private static final long BINDING_ID = 42L;

    @Mock
    private JDA jda;

    @Mock
    private LeaderboardService leaderboardService;

    private LeaderboardScheduler leaderboardScheduler;

    @BeforeEach
    void setUp() {
        // Run every stage on the calling thread so the tests can verify right after the update returns
        leaderboardScheduler = spy(new LeaderboardScheduler(jda, leaderboardService, Runnable::run));
        lenient().doReturn(CompletableFuture.completedFuture(null))
                .when(leaderboardScheduler).delay();
    }

    @Test
    void updateLeaderboards_channelNotFound_removesBindingIfStillInChannel() {
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding(List.of("msg-1"))));
        when(jda.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(null);

        leaderboardScheduler.updateLeaderboards();

        verify(leaderboardService).removeBindingIfInChannel(BINDING_ID, CHANNEL_ID);
    }

    @Test
    void updateLeaderboards_guildUnavailable_keepsBinding() {
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding(List.of("msg-1"))));
        when(jda.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(null);
        when(jda.getUnavailableGuilds()).thenReturn(Set.of(GUILD_ID));

        leaderboardScheduler.updateLeaderboards();

        verify(leaderboardService, never()).removeBindingIfInChannel(any(), any());
    }

    @Test
    void updateLeaderboards_apiFails_skipsWithoutRemovingBinding() {
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding(List.of("msg-1"))));
        StandardGuildMessageChannel channel = accessibleChannel();
        when(jda.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(channel);
        when(leaderboardService.fetchLeaderboard(StatisticType.PRESTIGE, 1)).thenReturn(Optional.empty());

        leaderboardScheduler.updateLeaderboards();

        verifyNothingRemoved();
        verify(channel, never()).editMessageEmbedsById(any(String.class), any(MessageEmbed.class));
    }

    @Test
    void updateLeaderboards_success_editsMessages() {
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding(List.of("msg-1", "msg-2"))));
        StandardGuildMessageChannel channel = accessibleChannel();
        Guild guild = mock(Guild.class);
        when(jda.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(channel);
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
        DiscordLeaderboardResponse response = emptyResponse();
        when(leaderboardService.fetchLeaderboard(eq(StatisticType.PRESTIGE), any(int.class)))
                .thenReturn(Optional.of(response));

        MessageEmbed embedPage1 = mock(MessageEmbed.class);
        MessageEmbed embedPage2 = mock(MessageEmbed.class);
        when(leaderboardService.buildLeaderboardEmbed(GUILD_ID, guild, StatisticType.PRESTIGE, response, true, true, false))
                .thenReturn(embedPage1);
        when(leaderboardService.buildLeaderboardEmbed(GUILD_ID, guild, StatisticType.PRESTIGE, response, false, true, false))
                .thenReturn(embedPage2);
        stubEdit(channel, "msg-1", embedPage1, CompletableFuture.completedFuture(null));
        stubEdit(channel, "msg-2", embedPage2, CompletableFuture.completedFuture(null));

        leaderboardScheduler.updateLeaderboards();

        verify(channel).editMessageEmbedsById("msg-1", embedPage1);
        verify(channel).editMessageEmbedsById("msg-2", embedPage2);
    }

    @Test
    void updateLeaderboards_pageThrows_continuesWithNextPage() {
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding(List.of("msg-1", "msg-2"))));
        StandardGuildMessageChannel channel = accessibleChannel();
        Guild guild = mock(Guild.class);
        when(jda.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(channel);
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
        DiscordLeaderboardResponse response = emptyResponse();
        when(leaderboardService.fetchLeaderboard(eq(StatisticType.PRESTIGE), any(int.class)))
                .thenReturn(Optional.of(response));

        MessageEmbed embedPage2 = mock(MessageEmbed.class);
        when(leaderboardService.buildLeaderboardEmbed(GUILD_ID, guild, StatisticType.PRESTIGE, response, true, true, false))
                .thenThrow(new IllegalStateException("boom"));
        when(leaderboardService.buildLeaderboardEmbed(GUILD_ID, guild, StatisticType.PRESTIGE, response, false, true, false))
                .thenReturn(embedPage2);
        stubEdit(channel, "msg-2", embedPage2, CompletableFuture.completedFuture(null));

        leaderboardScheduler.updateLeaderboards();

        verify(channel).editMessageEmbedsById("msg-2", embedPage2);
        verifyNothingRemoved();
    }

    @Test
    void updateLeaderboards_multipleMissingChannels_processesAll() {
        LeaderboardBinding binding1 = binding(List.of("msg-1"));
        LeaderboardBinding binding2 = new LeaderboardBinding(
                43L, "guild-2", StatisticType.DEATHS, "channel-2", List.of("msg-2"), 1);
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding1, binding2));
        when(jda.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(null);
        when(jda.getChannelById(StandardGuildMessageChannel.class, "channel-2")).thenReturn(null);

        leaderboardScheduler.updateLeaderboards();

        verify(leaderboardService).removeBindingIfInChannel(BINDING_ID, CHANNEL_ID);
        verify(leaderboardService).removeBindingIfInChannel(43L, "channel-2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateLeaderboards_unknownMessage_removesBindingDeletesOtherPagesAndSkipsThem() {
        LeaderboardBinding binding = binding(List.of("msg-1", "msg-2"));
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding));
        StandardGuildMessageChannel channel = accessibleChannel();
        when(channel.getId()).thenReturn(CHANNEL_ID);
        MessageEmbed embed = stubPageOne(channel);
        stubEdit(channel, "msg-1", embed, failedWith(unknownMessage()));
        when(leaderboardService.removeBindingIfContainsMessage(BINDING_ID, "msg-1")).thenReturn(Optional.of(binding));
        AuditableRestAction<Void> delete = mock(AuditableRestAction.class);
        when(channel.deleteMessageById("msg-2")).thenReturn(delete);

        leaderboardScheduler.updateLeaderboards();

        verify(leaderboardService).removeBindingIfContainsMessage(BINDING_ID, "msg-1");
        verify(channel).deleteMessageById("msg-2");
        verify(leaderboardService, never()).fetchLeaderboard(StatisticType.PRESTIGE, 2);
    }

    @Test
    void updateLeaderboards_staleUnknownMessage_keepsReplacedBinding() {
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding(List.of("msg-1"))));
        StandardGuildMessageChannel channel = accessibleChannel();
        MessageEmbed embed = stubPageOne(channel);
        stubEdit(channel, "msg-1", embed, failedWith(unknownMessage()));
        when(leaderboardService.removeBindingIfContainsMessage(BINDING_ID, "msg-1")).thenReturn(Optional.empty());

        leaderboardScheduler.updateLeaderboards();

        verify(channel, never()).deleteMessageById(anyString());
    }

    @Test
    void updateLeaderboards_otherError_doesNotRemoveBinding() {
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding(List.of("msg-1"))));
        StandardGuildMessageChannel channel = accessibleChannel();
        MessageEmbed embed = stubPageOne(channel);
        stubEdit(channel, "msg-1", embed, failedWith(new RuntimeException("rate limited")));

        leaderboardScheduler.updateLeaderboards();

        verifyNothingRemoved();
    }

    @Test
    void updateLeaderboards_channelObfuscated_skipsWithoutRemovingBinding() {
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding(List.of("msg-1"))));
        StandardGuildMessageChannel channel = mock(StandardGuildMessageChannel.class);
        when(channel.isObfuscated()).thenReturn(true);
        when(jda.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(channel);

        leaderboardScheduler.updateLeaderboards();

        verifyNothingRemoved();
        verify(leaderboardService, never()).fetchLeaderboard(any(), any(int.class));
        verify(channel, never()).editMessageEmbedsById(any(String.class), any(MessageEmbed.class));
    }

    @Test
    void updateLeaderboards_noChannelAccess_skipsWithoutRemovingBinding() {
        when(leaderboardService.getAllBindings()).thenReturn(List.of(binding(List.of("msg-1"))));
        StandardGuildMessageChannel channel = channelWithAccess(false);
        when(jda.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(channel);

        leaderboardScheduler.updateLeaderboards();

        verifyNothingRemoved();
        verify(leaderboardService, never()).fetchLeaderboard(any(), any(int.class));
        verify(channel, never()).editMessageEmbedsById(any(String.class), any(MessageEmbed.class));
    }

    @Test
    void updateLeaderboards_noBindings_doesNothing() {
        when(leaderboardService.getAllBindings()).thenReturn(List.of());

        leaderboardScheduler.updateLeaderboards();

        verify(jda, never()).getChannelById(eq(StandardGuildMessageChannel.class), anyString());
        verify(jda, never()).getChannelById(eq(StandardGuildMessageChannel.class), anyLong());
    }

    private void verifyNothingRemoved() {
        verify(leaderboardService, never()).removeBindingIfInChannel(any(), any());
        verify(leaderboardService, never()).removeBindingIfContainsMessage(any(), any());
    }

    private static LeaderboardBinding binding(List<String> messageIds) {
        return new LeaderboardBinding(BINDING_ID, GUILD_ID, StatisticType.PRESTIGE, CHANNEL_ID, messageIds,
                messageIds.size());
    }

    private static DiscordLeaderboardResponse emptyResponse() {
        DiscordLeaderboardResponse response = new DiscordLeaderboardResponse();
        response.setResults(List.of());
        return response;
    }

    private MessageEmbed stubPageOne(StandardGuildMessageChannel channel) {
        Guild guild = mock(Guild.class);
        when(jda.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(channel);
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
        DiscordLeaderboardResponse response = emptyResponse();
        when(leaderboardService.fetchLeaderboard(StatisticType.PRESTIGE, 1)).thenReturn(Optional.of(response));
        MessageEmbed embed = mock(MessageEmbed.class);
        when(leaderboardService.buildLeaderboardEmbed(GUILD_ID, guild, StatisticType.PRESTIGE, response, true, true, false))
                .thenReturn(embed);
        return embed;
    }

    private static void stubEdit(StandardGuildMessageChannel channel, String messageId, MessageEmbed embed,
                                 CompletableFuture<Message> result) {
        MessageEditAction editAction = mock(MessageEditAction.class);
        when(channel.editMessageEmbedsById(messageId, embed)).thenReturn(editAction);
        when(editAction.submit()).thenReturn(result);
    }

    private static ErrorResponseException unknownMessage() {
        ErrorResponseException error = mock(ErrorResponseException.class);
        when(error.getErrorResponse()).thenReturn(ErrorResponse.UNKNOWN_MESSAGE);
        return error;
    }

    private static CompletableFuture<Message> failedWith(Throwable error) {
        CompletableFuture<Message> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    private StandardGuildMessageChannel accessibleChannel() {
        return channelWithAccess(true);
    }

    private StandardGuildMessageChannel channelWithAccess(boolean hasAccess) {
        StandardGuildMessageChannel channel = mock(StandardGuildMessageChannel.class);
        Guild channelGuild = mock(Guild.class);
        SelfMember selfMember = mock(SelfMember.class);
        when(channel.getGuild()).thenReturn(channelGuild);
        when(channelGuild.getSelfMember()).thenReturn(selfMember);
        when(selfMember.hasAccess(channel)).thenReturn(hasAccess);
        return channel;
    }
}
