package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublicLeaderboardsCommand {

    private final GuildServerRepository guildServerRepository;
    private final MessageService messageService;

    public void onPublicLeaderboards(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        boolean enabled = event.getOption("enabled").getAsBoolean();

        GuildServer server = guildServerRepository.findById(guildId)
                .orElseGet(() -> new GuildServer(guildId));
        server.setLeaderboardsPublic(enabled);
        guildServerRepository.save(server);

        String messageKey = enabled ? "setup.public_leaderboards.enabled" : "setup.public_leaderboards.disabled";
        event.reply(messageService.getMessage(guildId, messageKey))
                .setEphemeral(true).queue();
    }
}
