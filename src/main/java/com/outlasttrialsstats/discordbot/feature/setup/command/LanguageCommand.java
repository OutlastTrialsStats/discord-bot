package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.shared.MessageService;
import io.github.kaktushose.jdac.annotations.interactions.Choices;
import io.github.kaktushose.jdac.annotations.interactions.Command;
import io.github.kaktushose.jdac.annotations.interactions.CommandConfig;
import io.github.kaktushose.jdac.annotations.interactions.Interaction;
import io.github.kaktushose.jdac.annotations.interactions.Param;
import io.github.kaktushose.jdac.dispatching.events.interactions.CommandEvent;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import org.springframework.stereotype.Component;

@Interaction
@Component
@RequiredArgsConstructor
public class LanguageCommand {

    private final MessageService messageService;

    @CommandConfig(enabledFor = Permission.ADMINISTRATOR)
    @Command("setup language")
    public void onLanguage(CommandEvent event,
                           @Choices({"English:en", "Deutsch:de"})
                           @Param("Language") String language) {
        String guildId = event.getGuild().getId();
        messageService.setLanguage(guildId, language);
        event.with().ephemeral(true)
                .reply(messageService.getMessage(guildId, "setup.language.success", language));
    }
}
