package com.outlasttrialsstats.discordbot.feature.profile.command;

import com.outlasttrialsstats.discordbot.feature.profile.service.GuildSyncService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import io.github.kaktushose.jdac.annotations.interactions.Command;
import io.github.kaktushose.jdac.annotations.interactions.CommandConfig;
import io.github.kaktushose.jdac.annotations.interactions.Interaction;
import io.github.kaktushose.jdac.dispatching.events.interactions.CommandEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.stereotype.Component;

@Interaction
@Component
@RequiredArgsConstructor
@Slf4j
public class SyncAllCommand {

    private final GuildSyncService guildSyncService;
    private final MessageService messageService;

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command(value = "sync-all", desc = "Sync roles for all members in this server")
    public void onSyncAll(CommandEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();

        event.jdaEvent().deferReply(true).queue();
        InteractionHook hook = event.jdaEvent().getHook();

        hook.editOriginal(messageService.getMessage(guildId, "sync.starting")).queue();

        guild.loadMembers().onSuccess(members -> {
            var result = guildSyncService.syncMembers(guild, members);

            hook.editOriginal(messageService.getMessage(guildId, "sync.completed",
                    result.updated(), result.skipped())).queue();
        });
    }
}
