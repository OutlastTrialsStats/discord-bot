package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NicknameCommand {

    private final GuildServerRepository guildServerRepository;
    private final MessageService messageService;

    public void onNickname(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);
        boolean enabled = event.getOption("enabled").getAsBoolean();

        if (enabled && !guild.getSelfMember().hasPermission(Permission.NICKNAME_MANAGE)) {
            event.reply(messageService.getMessage(guildId, "error.missing_permission.manage_nicknames"))
                    .setEphemeral(true).queue();
            return;
        }

        GuildServer server = guildServerRepository.findById(guildId)
                .orElseGet(() -> new GuildServer(guildId));
        server.setAutoNickname(enabled);
        guildServerRepository.save(server);

        String messageKey = enabled ? "setup.nickname.enabled" : "setup.nickname.disabled";
        event.reply(messageService.getMessage(guildId, messageKey))
                .setEphemeral(true).queue();
    }
}
