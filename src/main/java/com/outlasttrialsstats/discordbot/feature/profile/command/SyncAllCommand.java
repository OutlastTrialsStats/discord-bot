package com.outlasttrialsstats.discordbot.feature.profile.command;

import com.outlasttrialsstats.discordbot.feature.profile.service.GuildSyncService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SyncAllCommand {

    private final GuildSyncService guildSyncService;
    private final MessageService messageService;

    public void onSyncAll(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();

        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        hook.editOriginal(messageService.getMessage(guildId, "sync.starting")).queue();

        guild.loadMembers().onSuccess(members -> {
            var result = guildSyncService.syncMembers(guild, members);

            hook.editOriginal(messageService.getMessage(guildId, "sync.completed",
                    result.updated(), result.skipped())).queue();
        });
    }
}
