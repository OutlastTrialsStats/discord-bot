package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.shared.MessageService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LanguageCommand {

    private final MessageService messageService;

    public void onLanguage(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String language = event.getOption("language").getAsString();
        messageService.setLanguage(guildId, language);
        event.reply(messageService.getMessage(guildId, "setup.language.success", language))
                .setEphemeral(true).queue();
    }
}
