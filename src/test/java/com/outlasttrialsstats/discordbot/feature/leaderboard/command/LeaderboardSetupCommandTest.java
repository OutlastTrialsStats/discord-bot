package com.outlasttrialsstats.discordbot.feature.leaderboard.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
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
        when(selfMember.hasPermission(channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)).thenReturn(false);

        command.onSetupLeaderboard(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal("error.missing_permission.channel_write");
        verify(leaderboardService, never()).fetchLeaderboard(any(), anyInt());
    }

    @Test
    void onSetupLeaderboard_fetchFails_editsDeferredReplyWithError() {
        when(channelUnion.getType()).thenReturn(ChannelType.TEXT);
        when(selfMember.hasPermission(channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)).thenReturn(true);
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
}
