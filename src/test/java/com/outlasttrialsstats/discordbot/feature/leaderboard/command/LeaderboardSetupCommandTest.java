package com.outlasttrialsstats.discordbot.feature.leaderboard.command;

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
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardBinding;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardSetupCommandTest {

    private static final String GUILD_ID = "guild-1";
    private static final StatisticType CATEGORY = StatisticType.COMPLETED_TRIALS;
    private static final String CHANNEL_ID = "channel-1";

    @Mock
    private LeaderboardService leaderboardService;

    @Mock
    private MessageService messageService;

    @Mock
    private SlashCommandInteractionEvent event;

    @Mock
    private Guild guild;

    @Mock
    private SelfMember selfMember;

    @Mock
    private GuildChannelUnion channelUnion;

    @Mock
    private StandardGuildMessageChannel channel;

    @Mock
    private InteractionHook hook;

    private LeaderboardSetupCommand command;

    @BeforeEach
    void setUp() {
        command = new LeaderboardSetupCommand(leaderboardService, messageService);

        var jda = mock(JDA.class);
        var channelOption = mock(OptionMapping.class);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn(GUILD_ID);
        when(event.getJDA()).thenReturn(jda);
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
        when(event.deferReply(true)).thenReturn(mock(ReplyCallbackAction.class));
        when(event.getHook()).thenReturn(hook);
        when(hook.editOriginal(anyString())).thenReturn(mock(WebhookMessageEditAction.class));
        when(event.getOption("channel")).thenReturn(channelOption);
        when(channelOption.getAsChannel()).thenReturn(channelUnion);
        lenient().when(channelUnion.asStandardGuildMessageChannel()).thenReturn(channel);
        lenient().when(guild.getSelfMember()).thenReturn(selfMember);
        lenient().when(messageService.getMessage(eq(GUILD_ID), anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(1));
    }

    @Test
    void onSetupLeaderboard_invalidChannelType_editsDeferredReply() {
        when(channelUnion.getType()).thenReturn(ChannelType.VOICE);

        command.onSetupLeaderboard(event);

        var order = inOrder(event, hook);
        order.verify(event).deferReply(true);
        order.verify(hook).editOriginal("error.invalid_channel_type");
        verify(event, never()).reply(anyString());
    }

    @Test
    void onSetupLeaderboard_missingChannelPermission_editsDeferredReply() {
        when(channelUnion.getType()).thenReturn(ChannelType.TEXT);
        when(selfMember.hasPermission(channel, LeaderboardSetupCommand.REQUIRED_PERMISSIONS)).thenReturn(false);

        command.onSetupLeaderboard(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal("error.missing_permission.channel_write");
        assertThat(LeaderboardSetupCommand.REQUIRED_PERMISSIONS).contains(
                Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
                Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_HISTORY);
        verify(leaderboardService, never()).fetchLeaderboard(any(), anyInt());
    }

    @Test
    void onSetupLeaderboard_fetchFails_editsDeferredReplyWithError() {
        when(channelUnion.getType()).thenReturn(ChannelType.TEXT);
        when(selfMember.hasPermission(channel, LeaderboardSetupCommand.REQUIRED_PERMISSIONS)).thenReturn(true);
        var categoryOption = mock(OptionMapping.class);
        var pagesOption = mock(OptionMapping.class);
        when(event.getOption("category")).thenReturn(categoryOption);
        when(categoryOption.getAsString()).thenReturn(CATEGORY.getValue());
        when(event.getOption("pages")).thenReturn(pagesOption);
        when(pagesOption.getAsInt()).thenReturn(3);
        when(leaderboardService.fetchLeaderboard(CATEGORY, 1)).thenReturn(Optional.empty());

        command.onSetupLeaderboard(event);

        var order = inOrder(event, leaderboardService, hook);
        order.verify(event).deferReply(true);
        order.verify(leaderboardService).fetchLeaderboard(CATEGORY, 1);
        order.verify(hook).editOriginal("leaderboard.error");
        verify(leaderboardService, never()).removeBinding(any(), any());
    }

    @Test
    void onSetupLeaderboard_existingBinding_postsAndSavesBeforeDeletingOldMessages() {
        stubValidRequest(2);
        stubPages(2);
        when(leaderboardService.saveBinding(GUILD_ID, CATEGORY, CHANNEL_ID, List.of("new-1", "new-2"), 2))
                .thenReturn(Optional.of(new LeaderboardBinding(
                        1L, GUILD_ID, CATEGORY, "old-channel", List.of("old-1", "old-2"), 2)));
        StandardGuildMessageChannel oldChannel = mock(StandardGuildMessageChannel.class);
        when(guild.getChannelById(StandardGuildMessageChannel.class, "old-channel")).thenReturn(oldChannel);
        when(oldChannel.deleteMessageById(anyString())).thenReturn(deleteAction());

        command.onSetupLeaderboard(event);

        var order = inOrder(channel, leaderboardService, oldChannel, hook);
        order.verify(channel, times(2)).sendMessageEmbeds(any(MessageEmbed.class));
        order.verify(leaderboardService).saveBinding(GUILD_ID, CATEGORY, CHANNEL_ID, List.of("new-1", "new-2"), 2);
        order.verify(oldChannel).deleteMessageById("old-1");
        order.verify(oldChannel).deleteMessageById("old-2");
        order.verify(hook).editOriginal("setup.leaderboard.success");
        verify(leaderboardService, never()).removeBinding(any(), any());
    }

    @Test
    void onSetupLeaderboard_postFails_deletesPartialPostsAndKeepsOldBinding() {
        stubValidRequest(2);
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

        command.onSetupLeaderboard(event);

        verify(channel).deleteMessageById("new-1");
        verify(leaderboardService, never()).saveBinding(any(), any(), any(), any(), anyInt());
        verify(leaderboardService, never()).removeBinding(any(), any());
        verify(hook).editOriginal("setup.leaderboard.post_failed");
    }

    @Test
    void onSetupLeaderboard_saveFails_deletesPostedMessages() {
        stubValidRequest(1);
        stubPages(1);
        when(leaderboardService.saveBinding(any(), any(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("db down"));
        when(guild.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(channel);
        when(channel.deleteMessageById(anyString())).thenReturn(deleteAction());

        command.onSetupLeaderboard(event);

        verify(channel).deleteMessageById("new-1");
        verify(hook).editOriginal("setup.leaderboard.post_failed");
    }

    @Test
    void onSetupLeaderboard_zeroTotalPages_stillPostsOnePage() {
        stubValidRequest(3);
        DiscordLeaderboardResponse response = responseWithPages(0);
        when(leaderboardService.fetchLeaderboard(CATEGORY, 1)).thenReturn(Optional.of(response));
        MessageEmbed embed = mock(MessageEmbed.class);
        when(leaderboardService.buildLeaderboardEmbed(GUILD_ID, guild, CATEGORY, response, true, true, false))
                .thenReturn(embed);
        MessageCreateAction send = sendAction("new-1");
        when(channel.sendMessageEmbeds(embed)).thenReturn(send);
        when(leaderboardService.saveBinding(GUILD_ID, CATEGORY, CHANNEL_ID, List.of("new-1"), 1))
                .thenReturn(Optional.empty());

        command.onSetupLeaderboard(event);

        verify(channel).sendMessageEmbeds(embed);
        verify(leaderboardService).saveBinding(GUILD_ID, CATEGORY, CHANNEL_ID, List.of("new-1"), 1);
        verify(hook).editOriginal("setup.leaderboard.success");
    }

    private void stubValidRequest(int pages) {
        when(channelUnion.getType()).thenReturn(ChannelType.TEXT);
        when(selfMember.hasPermission(channel, LeaderboardSetupCommand.REQUIRED_PERMISSIONS)).thenReturn(true);
        lenient().when(channel.getId()).thenReturn(CHANNEL_ID);
        var categoryOption = mock(OptionMapping.class);
        var pagesOption = mock(OptionMapping.class);
        when(event.getOption("category")).thenReturn(categoryOption);
        when(categoryOption.getAsString()).thenReturn(CATEGORY.getValue());
        when(event.getOption("pages")).thenReturn(pagesOption);
        when(pagesOption.getAsInt()).thenReturn(pages);
    }

    /** Stubs {@code count} pages; page n is posted as message "new-n". */
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
