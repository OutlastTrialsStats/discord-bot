package com.outlasttrialsstats.discordbot.feature.setup.service;

import com.outlasttrialsstats.discordbot.feature.setup.RoleSafety;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService.MappedRole;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleCleanupService {

    private final RoleMappingService roleMappingService;
    private final MessageService messageService;

    public void deleteAll(Guild guild, Consumer<String> reply) {
        String guildId = guild.getId();

        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            reply.accept(messageService.getMessage(guildId, "error.missing_permission.manage_roles"));
            return;
        }

        List<MappedRole> mappedRoles = roleMappingService.getMappedRoles(guildId);

        if (mappedRoles.isEmpty()) {
            reply.accept(messageService.getMessage(guildId, "setup.delete.no_roles"));
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
                reply.accept(messageService.getMessage(guildId, "setup.delete.completed",
                        deleted.get(), unlinked.get(), failed.get()));
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
