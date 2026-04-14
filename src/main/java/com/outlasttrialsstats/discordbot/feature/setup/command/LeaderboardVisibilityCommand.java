package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LeaderboardVisibilityCommand {

    private final GuildServerRepository guildServerRepository;
    private final MessageService messageService;

    public void onLeaderboardVisibility(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        boolean enabled = event.getOption("enabled").getAsBoolean();

        GuildServer server = guildServerRepository.findById(guildId)
                .orElseGet(() -> new GuildServer(guildId));
        server.setLeaderboardPublic(enabled);
        guildServerRepository.save(server);

        String messageKey = enabled ? "setup.leaderboard_visibility.public" : "setup.leaderboard_visibility.private";
        event.reply(messageService.getMessage(guildId, messageKey))
                .setEphemeral(true).queue();
    }
}
