package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteCommand {

    private final RoleMappingService roleMappingService;
    private final MessageService messageService;

    public void onDelete(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();

        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        List<String> roleIds = roleMappingService.getAllRoleIds(guildId);

        if (roleIds.isEmpty()) {
            hook.editOriginal(messageService.getMessage(guildId, "setup.delete.no_roles")).queue();
            return;
        }

        AtomicInteger pending = new AtomicInteger(0);
        AtomicInteger deleted = new AtomicInteger(0);

        Runnable onComplete = () -> {
            roleMappingService.deleteAllMappings(guildId);
            hook.editOriginal(messageService.getMessage(guildId, "setup.delete.completed", deleted.get())).queue();
        };

        for (String roleId : roleIds) {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                pending.incrementAndGet();
                role.delete().queue(
                        _ -> {
                            deleted.incrementAndGet();
                            if (pending.decrementAndGet() == 0) onComplete.run();
                        },
                        error -> {
                            log.warn("Failed to delete role {} in guild {}: {}", roleId, guildId, error.getMessage());
                            if (pending.decrementAndGet() == 0) onComplete.run();
                        }
                );
            }
        }

        if (pending.get() == 0) {
            onComplete.run();
        }
    }
}
