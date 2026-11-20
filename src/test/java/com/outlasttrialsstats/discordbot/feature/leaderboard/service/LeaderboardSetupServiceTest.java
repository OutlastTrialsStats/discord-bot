package com.outlasttrialsstats.discordbot.feature.leaderboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardSetupServiceTest {

    private static final String GUILD_ID = "guild-1";
    private static final StatisticType CATEGORY = StatisticType.COMPLETED_TRIALS;
    private static final String CHANNEL_ID = "channel-1";

    @Mock
    private LeaderboardService leaderboardService;

    @Mock
    private MessageService messageService;

    @Mock
    private Guild guild;

    @Mock
    private SelfMember selfMember;

    @Mock
    private StandardGuildMessageChannel channel;

    private LeaderboardSetupService setupService;

    @BeforeEach
    void setUp() {
        setupService = new LeaderboardSetupService(leaderboardService, messageService);
        when(guild.getId()).thenReturn(GUILD_ID);
        lenient().when(guild.getSelfMember()).thenReturn(selfMember);
        lenient().when(channel.getId()).thenReturn(CHANNEL_ID);
        lenient().when(messageService.getMessage(eq(GUILD_ID), anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(1));
    }

    @Test
    void create_missingChannelPermission_rejectedWithoutFetching() {
        when(selfMember.hasPermission(channel, LeaderboardSetupService.REQUIRED_PERMISSIONS)).thenReturn(false);

        assertThat(setupService.create(guild, channel, CATEGORY, 3)).isEqualTo("error.missing_permission.channel_write");

        assertThat(LeaderboardSetupService.REQUIRED_PERMISSIONS).contains(
                Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
                Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_HISTORY);
        verify(leaderboardService, never()).fetchLeaderboard(any(), anyInt());
    }

    @Test
    void create_fetchFails_repliesWithError() {
        allowChannel();
        when(leaderboardService.fetchLeaderboard(CATEGORY, 1)).thenReturn(Optional.empty());

        assertThat(setupService.create(guild, channel, CATEGORY, 3)).isEqualTo("leaderboard.error");
    }

    @Test
    void create_existingBinding_postsAndSavesBeforeDeletingOldMessages() {
        allowChannel();
        stubPages(2);
        when(leaderboardService.saveBinding(GUILD_ID, CATEGORY, CHANNEL_ID, List.of("new-1", "new-2"), 2))
                .thenReturn(Optional.of(new LeaderboardBinding(
                        1L, GUILD_ID, CATEGORY, "old-channel", List.of("old-1", "old-2"), 2)));
        StandardGuildMessageChannel oldChannel = mock(StandardGuildMessageChannel.class);
        when(guild.getChannelById(StandardGuildMessageChannel.class, "old-channel")).thenReturn(oldChannel);
        when(oldChannel.deleteMessageById(anyString())).thenReturn(deleteAction());

        String reply = setupService.create(guild, channel, CATEGORY, 2);

        var order = inOrder(channel, leaderboardService, oldChannel);
        order.verify(channel, times(2)).sendMessageEmbeds(any(MessageEmbed.class));
        order.verify(leaderboardService).saveBinding(GUILD_ID, CATEGORY, CHANNEL_ID, List.of("new-1", "new-2"), 2);
        order.verify(oldChannel).deleteMessageById("old-1");
        order.verify(oldChannel).deleteMessageById("old-2");
        assertThat(reply).isEqualTo("setup.leaderboard.success");
    }

    @Test
    void create_postFails_deletesPartialPostsAndKeepsOldBinding() {
        allowChannel();
        MessageEmbed embed = mock(MessageEmbed.class);
        DiscordLeaderboardResponse response = responseWithPages(2);
        when(leaderboardService.fetchLeaderboard(CATEGORY, 1)).thenReturn(Optional.of(response));
        when(leaderboardService.fetchLeaderboard(CATEGORY, 2)).thenReturn(Optional.of(response));
        when(leaderboardService.buildLeaderboardEmbed(eq(GUILD_ID), eq(guild), eq(CATEGORY), eq(response),
                anyBoolean(), eq(true), eq(false))).thenReturn(embed);
        MessageCreateAction firstSend = sendAction("new-1");
        MessageCreateAction failingSend = mock(MessageCreateAction.class);
        when(failingSend.complete()).thenThrow(new IllegalStateException("missing access"));
        when(channel.sendMessageEmbeds(embed)).thenReturn(firstSend, failingSend);
        when(guild.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(channel);
        when(channel.deleteMessageById(anyString())).thenReturn(deleteAction());

        assertThat(setupService.create(guild, channel, CATEGORY, 2)).isEqualTo("setup.leaderboard.post_failed");

        verify(channel).deleteMessageById("new-1");
        verify(leaderboardService, never()).saveBinding(any(), any(), any(), any(), anyInt());
    }

    @Test
    void create_saveFails_deletesPostedMessages() {
        allowChannel();
        stubPages(1);
        when(leaderboardService.saveBinding(any(), any(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("db down"));
        when(guild.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(channel);
        when(channel.deleteMessageById(anyString())).thenReturn(deleteAction());

        assertThat(setupService.create(guild, channel, CATEGORY, 1)).isEqualTo("setup.leaderboard.post_failed");

        verify(channel).deleteMessageById("new-1");
    }

    @Test
    void create_zeroTotalPages_stillPostsOnePage() {
        allowChannel();
        DiscordLeaderboardResponse response = responseWithPages(0);
        when(leaderboardService.fetchLeaderboard(CATEGORY, 1)).thenReturn(Optional.of(response));
        MessageEmbed embed = mock(MessageEmbed.class);
        when(leaderboardService.buildLeaderboardEmbed(GUILD_ID, guild, CATEGORY, response, true, true, false))
                .thenReturn(embed);
        MessageCreateAction send = sendAction("new-1");
        when(channel.sendMessageEmbeds(embed)).thenReturn(send);
        when(leaderboardService.saveBinding(GUILD_ID, CATEGORY, CHANNEL_ID, List.of("new-1"), 1))
                .thenReturn(Optional.empty());

        assertThat(setupService.create(guild, channel, CATEGORY, 3)).isEqualTo("setup.leaderboard.success");

        verify(channel).sendMessageEmbeds(embed);
        verify(leaderboardService).saveBinding(GUILD_ID, CATEGORY, CHANNEL_ID, List.of("new-1"), 1);
    }

    @Test
    void remove_existingBinding_deletesMessages() {
        when(leaderboardService.removeBinding(GUILD_ID, 7L)).thenReturn(Optional.of(new LeaderboardBinding(
                7L, GUILD_ID, CATEGORY, CHANNEL_ID, List.of("m-1", "m-2"), 2)));
        when(guild.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(channel);
        when(channel.deleteMessageById(anyString())).thenReturn(deleteAction());

        assertThat(setupService.remove(guild, 7L)).isEqualTo("setup.leaderboard.remove.success");

        verify(channel).deleteMessageById("m-1");
        verify(channel).deleteMessageById("m-2");
    }

    @Test
    void remove_unknownBinding_repliesNothing() {
        when(leaderboardService.removeBinding(GUILD_ID, 7L)).thenReturn(Optional.empty());

        assertThat(setupService.remove(guild, 7L)).isEqualTo("setup.leaderboard.remove.nothing");

        verify(guild, never()).getChannelById(eq(StandardGuildMessageChannel.class), anyString());
    }

    private void allowChannel() {
        when(selfMember.hasPermission(channel, LeaderboardSetupService.REQUIRED_PERMISSIONS)).thenReturn(true);
    }

    private void stubPages(int count) {
        DiscordLeaderboardResponse response = responseWithPages(count);
        for (int page = 1; page <= count; page++) {
            when(leaderboardService.fetchLeaderboard(CATEGORY, page)).thenReturn(Optional.of(response));
            MessageEmbed embed = mock(MessageEmbed.class);
            when(leaderboardService.buildLeaderboardEmbed(GUILD_ID, guild, CATEGORY, response, page == 1, true, false))
                    .thenReturn(embed);
            MessageCreateAction send = sendAction("new-" + page);
            when(channel.sendMessageEmbeds(embed)).thenReturn(send);
        }
    }

    private static DiscordLeaderboardResponse responseWithPages(int totalPages) {
        DiscordLeaderboardResponse response = new DiscordLeaderboardResponse();
        response.setResults(List.of());
        response.setTotalPages(totalPages);
        return response;
    }

    private static MessageCreateAction sendAction(String messageId) {
        MessageCreateAction send = mock(MessageCreateAction.class);
        Message message = mock(Message.class);
        when(message.getId()).thenReturn(messageId);
        when(send.complete()).thenReturn(message);
        return send;
    }

    @SuppressWarnings("unchecked")
    private static AuditableRestAction<Void> deleteAction() {
        return mock(AuditableRestAction.class);
    }
}
