package com.outlasttrialsstats.discordbot.feature.profile.command;

import com.outlasttrialsstats.discordbot.feature.profile.dto.RoleAssignmentResult;
import com.outlasttrialsstats.discordbot.feature.profile.service.RoleAssignmentService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import io.github.kaktushose.jdac.annotations.interactions.Command;
import io.github.kaktushose.jdac.annotations.interactions.CommandConfig;
import io.github.kaktushose.jdac.annotations.interactions.Interaction;
import io.github.kaktushose.jdac.dispatching.events.interactions.CommandEvent;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final RoleAssignmentService roleAssignmentService;
    private final MessageService messageService;

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command("sync-all")
    public void onSyncAll(CommandEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();

        event.jdaEvent().deferReply(true).queue();
        InteractionHook hook = event.jdaEvent().getHook();

        hook.editOriginal(messageService.getMessage(guildId, "sync.starting")).queue();

        guild.loadMembers().onSuccess(members -> {
            AtomicInteger updated = new AtomicInteger(0);
            AtomicInteger skipped = new AtomicInteger(0);

            for (var member : members) {
                if (member.getUser().isBot()) {
                    continue;
                }

                try {
                    RoleAssignmentResult result = roleAssignmentService.assignRoles(guild, member);
                    if (result.verified() && result.hasChanges()) {
                        updated.incrementAndGet();
                    } else {
                        skipped.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.warn("Failed to sync roles for member {}: {}", member.getId(), e.getMessage());
                    skipped.incrementAndGet();
                }
            }

            hook.editOriginal(messageService.getMessage(guildId, "sync.completed",
                    updated.get(), skipped.get())).queue();
        });
    }
}
