package com.outlasttrialsstats.discordbot.feature.setup.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEventMessagePublisher;
import com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEventMessagePublisher.Outcome;
import com.outlasttrialsstats.discordbot.feature.communityevent.command.CommunityEventSetupCommand;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventService;
import com.outlasttrialsstats.discordbot.feature.setup.service.BasicSetupService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.List;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StartBasicCommandWizardTest {

    private static final String GUILD_ID = "guild-1";
    private static final String CHANNEL_ID = "channel-1";

    private final NicknameCommand nicknameCommand = mock(NicknameCommand.class);
    private final CommunityEventMessagePublisher publisher = mock(CommunityEventMessagePublisher.class);
    private final CommunityEventService eventService = mock(CommunityEventService.class);
    private final CommunityEventSetupCommand eventSetupCommand = mock(CommunityEventSetupCommand.class);
    /** Returns the message key, so assertions can check which messages were shown. */
    private final MessageService messageService = mock(MessageService.class,
            invocation -> "getMessage".equals(invocation.getMethod().getName()) ? invocation.getArgument(1) : null);

    private StartBasicCommand command;

    @BeforeEach
    void setUp() {
        command = new StartBasicCommand(mock(BasicSetupService.class), messageService, nicknameCommand, publisher,
                eventService, eventSetupCommand);
    }

    @Test
    void enablingNicknamesStoresItAndShowsTheEventStep() {
        ButtonInteractionEvent event = button(StartBasicCommand.NICKNAME_ON);
        when(event.getGuild().getSelfMember().hasPermission(Permission.NICKNAME_MANAGE)).thenReturn(true);

        command.onButton(event);

        verify(nicknameCommand).setAutoNickname(GUILD_ID, true);
        verify(event).editMessage(argThat((String text) -> text.startsWith("setup.nickname.enabled")
                && text.endsWith("setup.start.event_prompt")));
    }

    @Test
    void enablingNicknamesWithoutPermissionKeepsThemOff() {
        ButtonInteractionEvent event = button(StartBasicCommand.NICKNAME_ON);
        when(event.getGuild().getSelfMember().hasPermission(Permission.NICKNAME_MANAGE)).thenReturn(false);

        command.onButton(event);

        verify(nicknameCommand, never()).setAutoNickname(anyString(), anyBoolean());
        verify(event).editMessage(argThat((String text) ->
                text.startsWith("error.missing_permission.manage_nicknames")));
    }

    @Test
    void skippingTheEventStepFinishesTheWizard() {
        ButtonInteractionEvent event = button(StartBasicCommand.EVENT_SKIP);

        command.onButton(event);

        verify(event).editMessage("setup.start.done");
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void choosingAChannelPostsTheProgressAndSetsTheAlertChannel() {
        TextChannel channel = mock(TextChannel.class);
        when(channel.getId()).thenReturn(CHANNEL_ID);
        EntitySelectInteractionEvent event = channelSelect(channel, true);
        Outcome posted = new Outcome.Posted("Escape", true);
        when(publisher.publish(event.getGuild(), channel, null)).thenReturn(posted);
        when(eventSetupCommand.describe(GUILD_ID, posted, channel)).thenReturn("posted");

        command.onEventChannelSelect(event);

        verify(publisher).publish(event.getGuild(), channel, null);
        verify(eventService).setAlertChannel(GUILD_ID, CHANNEL_ID);
        verify(event.getHook()).editOriginal(argThat((String text) -> text.startsWith("posted")
                && text.contains("setup.start.event_alerts") && text.endsWith("setup.start.done")));
    }

    @Test
    void withoutACurrentEventOnlyTheAlertChannelIsSet() {
        TextChannel channel = mock(TextChannel.class);
        when(channel.getId()).thenReturn(CHANNEL_ID);
        EntitySelectInteractionEvent event = channelSelect(channel, true);
        when(publisher.publish(event.getGuild(), channel, null)).thenReturn(new Outcome.NoCurrentEvent());

        command.onEventChannelSelect(event);

        verify(eventService).setAlertChannel(GUILD_ID, CHANNEL_ID);
        verify(event.getHook()).editOriginal(argThat((String text) ->
                text.startsWith("setup.start.event_no_current")));
    }

    @Test
    void aChannelWithoutPermissionsAsksAgain() {
        TextChannel channel = mock(TextChannel.class);
        EntitySelectInteractionEvent event = channelSelect(channel, false);

        command.onEventChannelSelect(event);

        verify(publisher, never()).publish(any(), any(), isNull());
        verify(eventService, never()).setAlertChannel(anyString(), anyString());
        verify(event).editMessage(argThat((String text) -> text.startsWith("error.missing_permission.channel_write")
                && text.endsWith("setup.start.event_prompt")));
    }

    private static ButtonInteractionEvent button(String componentId) {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getComponentId()).thenReturn(componentId);
        when(event.getGuild().getId()).thenReturn(GUILD_ID);
        return event;
    }

    private static EntitySelectInteractionEvent channelSelect(TextChannel channel, boolean canPost) {
        EntitySelectInteractionEvent event = mock(EntitySelectInteractionEvent.class, RETURNS_DEEP_STUBS);
        Guild guild = event.getGuild();
        when(guild.getId()).thenReturn(GUILD_ID);
        when(event.getMentions().getChannels()).thenReturn(List.<GuildChannel>of(channel));
        when(guild.getSelfMember().hasPermission(eq(channel), any(Permission[].class))).thenReturn(canPost);
        InteractionHook hook = mock(InteractionHook.class);
        @SuppressWarnings("unchecked")
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class, RETURNS_SELF);
        when(event.getHook()).thenReturn(hook);
        when(hook.editOriginal(anyString())).thenReturn(edit);
        return event;
    }
}
