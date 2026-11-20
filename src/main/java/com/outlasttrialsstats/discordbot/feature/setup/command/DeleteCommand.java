package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.feature.setup.RoleSafety;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService.MappedRole;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
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
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);

        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            event.reply(messageService.getMessage(guildId, "error.missing_permission.manage_roles"))
                    .setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        List<MappedRole> mappedRoles = roleMappingService.getMappedRoles(guildId);

        if (mappedRoles.isEmpty()) {
            hook.editOriginal(messageService.getMessage(guildId, "setup.delete.no_roles")).queue();
            return;
        }

        Member selfMember = guild.getSelfMember();
        AtomicInteger pending = new AtomicInteger(1);
        AtomicInteger deleted = new AtomicInteger(0);
        AtomicInteger unlinked = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        Collection<String> removableRoleIds = new ConcurrentLinkedQueue<>();

        Runnable taskDone = () -> {
            if (pending.decrementAndGet() == 0) {
                removeMappings(guildId, removableRoleIds);
                hook.editOriginal(messageService.getMessage(guildId, "setup.delete.completed",
                        deleted.get(), unlinked.get(), failed.get())).queue();
            }
        };

        for (MappedRole mappedRole : mappedRoles) {
            String roleId = mappedRole.roleId();
            Role role = guild.getRoleById(roleId);

            if (role == null || !mappedRole.botCreated()) {
                removableRoleIds.add(roleId);
                unlinked.incrementAndGet();
                continue;
            }

            if (RoleSafety.checkReachable(role, selfMember).isPresent()) {
                log.warn("Skipping deletion of role {} in guild {}: managed, public or above the bot", roleId, guildId);
                failed.incrementAndGet();
                continue;
            }

            pending.incrementAndGet();
            try {
                role.delete().queue(
                        _ -> {
                            removableRoleIds.add(roleId);
                            deleted.incrementAndGet();
                            taskDone.run();
                        },
                        error -> {
                            log.warn("Failed to delete role {} in guild {}: {}", roleId, guildId, error.getMessage());
                            failed.incrementAndGet();
                            taskDone.run();
                        }
                );
            } catch (Exception e) {
                log.warn("Failed to delete role {} in guild {}: {}", roleId, guildId, e.getMessage());
                failed.incrementAndGet();
                taskDone.run();
            }
        }

        taskDone.run();
    }

    private void removeMappings(String guildId, Collection<String> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        try {
            roleMappingService.deleteMappingsByRoleIds(guildId, List.copyOf(roleIds));
        } catch (Exception e) {
            log.warn("Failed to remove role mappings in guild {}: {}", guildId, e.getMessage());
        }
    }
}
