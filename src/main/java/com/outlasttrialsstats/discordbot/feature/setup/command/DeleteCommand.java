package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import io.github.kaktushose.jdac.annotations.interactions.Command;
import io.github.kaktushose.jdac.annotations.interactions.CommandConfig;
import io.github.kaktushose.jdac.annotations.interactions.Interaction;
import io.github.kaktushose.jdac.dispatching.events.interactions.CommandEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.stereotype.Component;

@Interaction
@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteCommand {

    private final RoleMappingService roleMappingService;
    private final MessageService messageService;

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command(value = "setup delete", desc = "Delete all bot-managed roles and remove their mappings from the database")
    public void onDelete(CommandEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();

        event.jdaEvent().deferReply(true).queue();
        InteractionHook hook = event.jdaEvent().getHook();

        List<String> roleIds = roleMappingService.getAllRoleIds(guildId);

        if (roleIds.isEmpty()) {
            hook.editOriginal(messageService.getMessage(guildId, "setup.delete.no_roles")).queue();
            return;
        }

        AtomicInteger pending = new AtomicInteger(0);
        AtomicInteger deleted = new AtomicInteger(0);

        for (String roleId : roleIds) {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                pending.incrementAndGet();
                role.delete().queue(
                        _ -> {
                            deleted.incrementAndGet();
                            if (pending.decrementAndGet() == 0) {
                                roleMappingService.deleteAllMappings(guildId);
                                hook.editOriginal(messageService.getMessage(guildId, "setup.delete.completed", deleted.get())).queue();
                            }
                        },
                        error -> {
                            log.warn("Failed to delete role {} in guild {}: {}", roleId, guildId, error.getMessage());
                            if (pending.decrementAndGet() == 0) {
                                roleMappingService.deleteAllMappings(guildId);
                                hook.editOriginal(messageService.getMessage(guildId, "setup.delete.completed", deleted.get())).queue();
                            }
                        }
                );
            }
        }

        // If no roles existed on Discord (all already deleted), just clean up DB
        if (pending.get() == 0) {
            roleMappingService.deleteAllMappings(guildId);
            hook.editOriginal(messageService.getMessage(guildId, "setup.delete.completed", 0)).queue();
        }
    }
}
