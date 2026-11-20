package com.outlasttrialsstats.discordbot.feature.communityevent.command;

import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEventEmbedFactory;
import com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEvents;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventService;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventSettings;
import com.outlasttrialsstats.discordbot.feature.setup.service.GuildSettingsService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CommunityEventCommand {

    public static final String NAME = "event";
    public static final String OPTION = "event";

    private final CommunityEventService service;
    private final CommunityEventSettings settings;
    private final CommunityEventEmbedFactory embedFactory;
    private final GuildSettingsService guildSettings;
    private final MessageService messageService;
    private final Clock clock;

    @Autowired
    public CommunityEventCommand(CommunityEventService service, CommunityEventSettings settings,
                                 CommunityEventEmbedFactory embedFactory, GuildSettingsService guildSettings,
                                 MessageService messageService) {
        this(service, settings, embedFactory, guildSettings, messageService, Clock.systemUTC());
    }

    CommunityEventCommand(CommunityEventService service, CommunityEventSettings settings,
                          CommunityEventEmbedFactory embedFactory, GuildSettingsService guildSettings,
                          MessageService messageService, Clock clock) {
        this.service = service;
        this.settings = settings;
        this.embedFactory = embedFactory;
        this.guildSettings = guildSettings;
        this.messageService = messageService;
        this.clock = clock;
    }

    public void onEvent(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String eventId = Optional.ofNullable(event.getOption(OPTION, null, OptionMapping::getAsString))
                .map(String::trim).filter(id -> !id.isEmpty()).orElse(null);

        event.deferReply(!guildSettings.get(guildId).isUserCommandsVisible()).queue();
        InteractionHook hook = event.getHook();

        Optional<List<DiscordCommunityEvent>> events = service.getEvents();
        if (events.isEmpty()) {
            hook.editOriginal(messageService.getMessage(guildId, "community_event.error")).queue();
            return;
        }
        Optional<DiscordCommunityEvent> shown = CommunityEvents.resolve(events.get(), eventId, clock.instant());
        if (shown.isEmpty()) {
            hook.editOriginal(eventId == null
                    ? messageService.getMessage(guildId, "setup.community_event.no_event")
                    : messageService.getMessage(guildId, "setup.community_event.unknown_event", eventId)).queue();
            return;
        }
        hook.editOriginalEmbeds(embedFactory.buildProgressEmbed(guildId, shown.get(), clock.instant())).queue();
    }

    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if (OPTION.equals(event.getFocusedOption().getName())) {
            event.replyChoices(settings.eventChoices(event.getFocusedOption().getValue())).queue();
        }
    }
}
