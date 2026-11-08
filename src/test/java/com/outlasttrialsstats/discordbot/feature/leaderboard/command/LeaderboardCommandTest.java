package com.outlasttrialsstats.discordbot.feature.leaderboard.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardCommandTest {

    private static final String GUILD_ID = "guild-1";
    private static final StatisticType CATEGORY = StatisticType.COMPLETED_TRIALS;

    @Mock
    private LeaderboardService leaderboardService;

    @Mock
    private MessageService messageService;

    @Mock
    private GuildServerRepository guildServerRepository;

    @Mock
    private Guild guild;

    @Mock
    private InteractionHook hook;

    @Mock
    private WebhookMessageEditAction<Message> editAction;

    private LeaderboardCommand command;

    @BeforeEach
    void setUp() {
        command = new LeaderboardCommand(leaderboardService, messageService, guildServerRepository);
        lenient().when(guild.getId()).thenReturn(GUILD_ID);
        lenient().when(hook.editOriginal(anyString())).thenReturn(editAction);
        lenient().when(hook.editOriginalEmbeds(any(MessageEmbed[].class))).thenReturn(editAction);
        lenient().when(editAction.setComponents(any(MessageTopLevelComponent[].class))).thenReturn(editAction);
        lenient().when(messageService.getMessage(eq(GUILD_ID), anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(1));
    }

    private SlashCommandInteractionEvent slashEvent() {
        var event = mock(SlashCommandInteractionEvent.class);
        var option = mock(OptionMapping.class);
        when(event.getGuild()).thenReturn(guild);
        when(event.getOption("category")).thenReturn(option);
        when(option.getAsString()).thenReturn(CATEGORY.getValue());
        when(event.deferReply(anyBoolean())).thenReturn(mock(ReplyCallbackAction.class));
        when(event.getHook()).thenReturn(hook);
        return event;
    }

    private static DiscordLeaderboardResponse response(int totalPages) {
        var data = new DiscordLeaderboardResponse();
        data.setTotalPages(totalPages);
        return data;
    }

    @Test
    void onLeaderboard_defersEphemeralBeforeFetch_whenUserCommandsHidden() {
        var event = slashEvent();
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.empty());
        var data = response(1);
        when(leaderboardService.fetchLeaderboard(CATEGORY, 1)).thenReturn(Optional.of(data));
        var embed = mock(MessageEmbed.class);
        when(leaderboardService.buildLeaderboardEmbed(GUILD_ID, guild, CATEGORY, data, true, true, true))
                .thenReturn(embed);

        command.onLeaderboard(event);

        var order = inOrder(event, leaderboardService, hook, editAction);
        order.verify(event).deferReply(true);
        order.verify(leaderboardService).fetchLeaderboard(CATEGORY, 1);
        order.verify(hook).editOriginalEmbeds(embed);
        order.verify(editAction).queue();
        verify(editAction, never()).setComponents(any(MessageTopLevelComponent[].class));
    }

    @Test
    void onLeaderboard_defersPublic_whenUserCommandsVisible() {
        var event = slashEvent();
        var server = mock(GuildServer.class);
        when(server.isUserCommandsVisible()).thenReturn(true);
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.of(server));
        var data = response(2);
        when(leaderboardService.fetchLeaderboard(CATEGORY, 1)).thenReturn(Optional.of(data));
        when(leaderboardService.buildLeaderboardEmbed(GUILD_ID, guild, CATEGORY, data, true, true, true))
                .thenReturn(mock(MessageEmbed.class));

        command.onLeaderboard(event);

        verify(event).deferReply(false);
        verify(editAction).setComponents(any(MessageTopLevelComponent[].class));
        verify(editAction).queue();
    }

    @Test
    void onLeaderboard_fetchFails_editsDeferredReplyWithError() {
        var event = slashEvent();
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.empty());
        when(leaderboardService.fetchLeaderboard(CATEGORY, 1)).thenReturn(Optional.empty());

        command.onLeaderboard(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal("leaderboard.error");
        verify(editAction).queue();
    }

    private ButtonInteractionEvent buttonEvent() {
        var event = mock(ButtonInteractionEvent.class);
        var jda = mock(JDA.class);
        when(event.getComponentId()).thenReturn("leaderboard:next:" + CATEGORY.getValue() + ":2");
        when(event.getGuild()).thenReturn(guild);
        when(event.getJDA()).thenReturn(jda);
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
        when(event.deferEdit()).thenReturn(mock(MessageEditCallbackAction.class));
        when(event.getHook()).thenReturn(hook);
        return event;
    }

    @Test
    void onButton_defersEditBeforeFetch() {
        var event = buttonEvent();
        var data = response(2);
        when(leaderboardService.fetchLeaderboard(CATEGORY, 2)).thenReturn(Optional.of(data));
        var embed = mock(MessageEmbed.class);
        when(leaderboardService.buildLeaderboardEmbed(GUILD_ID, guild, CATEGORY, data, true, true, true))
                .thenReturn(embed);

        command.onButton(event);

        var order = inOrder(event, leaderboardService, hook, editAction);
        order.verify(event).deferEdit();
        order.verify(leaderboardService).fetchLeaderboard(CATEGORY, 2);
        order.verify(hook).editOriginalEmbeds(embed);
        order.verify(editAction).queue();
        verify(event, never()).editMessageEmbeds(any(MessageEmbed[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onButton_fetchFails_sendsEphemeralFollowup() {
        var event = buttonEvent();
        when(leaderboardService.fetchLeaderboard(CATEGORY, 2)).thenReturn(Optional.empty());
        WebhookMessageCreateAction<Message> followup = mock(WebhookMessageCreateAction.class);
        when(hook.sendMessage("leaderboard.error")).thenReturn(followup);
        when(followup.setEphemeral(true)).thenReturn(followup);

        command.onButton(event);

        verify(event).deferEdit();
        verify(followup).queue();
        verify(hook, never()).editOriginalEmbeds(any(MessageEmbed[].class));
    }

    @Test
    void onButton_malformedId_doesNothing() {
        var event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn("leaderboard:broken");

        command.onButton(event);

        verify(event, never()).deferEdit();
        verify(leaderboardService, never()).fetchLeaderboard(any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
