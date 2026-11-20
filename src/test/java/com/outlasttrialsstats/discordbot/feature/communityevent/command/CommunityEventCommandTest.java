package com.outlasttrialsstats.discordbot.feature.communityevent.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEventEmbedFactory;
import com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEventTestData;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventService;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventSettings;
import com.outlasttrialsstats.discordbot.feature.setup.service.GuildSettingsService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
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
class CommunityEventCommandTest {

    private static final String GUILD_ID = "guild-1";
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    @Mock
    private CommunityEventService service;

    @Mock
    private CommunityEventSettings settings;

    @Mock
    private CommunityEventEmbedFactory embedFactory;

    @Mock
    private GuildSettingsService guildSettings;

    @Mock
    private MessageService messageService;

    @Mock
    private SlashCommandInteractionEvent event;

    @Mock
    private InteractionHook hook;

    private CommunityEventCommand command;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        command = new CommunityEventCommand(service, settings, embedFactory, guildSettings, messageService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn(GUILD_ID);
        when(event.getGuild()).thenReturn(guild);
        when(guildSettings.get(GUILD_ID)).thenReturn(new GuildServer(GUILD_ID));
        lenient().when(event.deferReply(true)).thenReturn(mock(ReplyCallbackAction.class));
        when(event.getHook()).thenReturn(hook);
        lenient().when(hook.editOriginal(anyString())).thenReturn(mock(WebhookMessageEditAction.class));
        lenient().when(hook.editOriginalEmbeds(any(MessageEmbed.class)))
                .thenReturn(mock(WebhookMessageEditAction.class, RETURNS_SELF));
        lenient().when(messageService.getMessage(eq(GUILD_ID), anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(1));
    }

    @Test
    void withoutOption_showsTheCurrentEvent() {
        DiscordCommunityEvent live = CommunityEventTestData.liveEvent("escape", 1_500, NOW.plusSeconds(3600));
        when(service.getEvents()).thenReturn(Optional.of(List.of(live)));
        MessageEmbed embed = mock(MessageEmbed.class);
        when(embedFactory.buildProgressEmbed(GUILD_ID, live, NOW)).thenReturn(embed);

        command.onEvent(event);

        verify(hook).editOriginalEmbeds(embed);
    }

    @Test
    void withEventOption_showsThatEvent() {
        DiscordCommunityEvent live = CommunityEventTestData.liveEvent("escape", 1_500, NOW.plusSeconds(3600));
        DiscordCommunityEvent other = CommunityEventTestData.liveEvent("characters_released", 10, null);
        when(service.getEvents()).thenReturn(Optional.of(List.of(live, other)));
        givenOption("characters_released");
        MessageEmbed embed = mock(MessageEmbed.class);
        when(embedFactory.buildProgressEmbed(GUILD_ID, other, NOW)).thenReturn(embed);

        command.onEvent(event);

        verify(hook).editOriginalEmbeds(embed);
    }

    @Test
    void unknownEvent_repliesWithError() {
        when(service.getEvents()).thenReturn(Optional.of(List.of()));
        givenOption("nope");

        command.onEvent(event);

        verify(hook).editOriginal("setup.community_event.unknown_event");
        verify(embedFactory, never()).buildProgressEmbed(any(), any(), any());
    }

    @Test
    void noCurrentEvent_repliesWithNotice() {
        when(service.getEvents()).thenReturn(Optional.of(List.of()));

        command.onEvent(event);

        verify(hook).editOriginal("setup.community_event.no_event");
    }

    @Test
    void fetchFails_repliesWithError() {
        when(service.getEvents()).thenReturn(Optional.empty());

        command.onEvent(event);

        verify(hook).editOriginal("community_event.error");
    }

    @Test
    void publicReplies_areNotEphemeral() {
        GuildServer server = new GuildServer(GUILD_ID);
        server.setUserCommandsVisible(true);
        when(guildSettings.get(GUILD_ID)).thenReturn(server);
        lenient().when(event.deferReply(false)).thenReturn(mock(ReplyCallbackAction.class));
        when(service.getEvents()).thenReturn(Optional.empty());

        command.onEvent(event);

        verify(event).deferReply(false);
    }

    @SuppressWarnings("unchecked")
    private void givenOption(String value) {
        OptionMapping option = mock(OptionMapping.class);
        when(option.getAsString()).thenReturn(value);
        when(event.getOption(eq(CommunityEventCommand.OPTION), any(), any())).thenAnswer(inv ->
                ((Function<OptionMapping, String>) inv.getArgument(2)).apply(option));
    }
}
