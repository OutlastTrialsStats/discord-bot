package com.outlasttrialsstats.discordbot.feature.setup.service;

import com.outlasttrialsstats.discordbot.entity.EnumRoleMapping;
import com.outlasttrialsstats.discordbot.entity.RankedRoleMapping;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.RoleSafety;
import com.outlasttrialsstats.discordbot.feature.setup.RoleTarget;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
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
public class RoleMappingActions {

    private final RoleMappingService roleMappingService;
    private final MessageService messageService;

    public record Mapping(RoleTarget target, String roleId) {
    }

    public List<Mapping> list(String guildId, RoleCategory category) {
        List<Mapping> mappings = new ArrayList<>();
        for (RankedRoleMapping mapping : roleMappingService.getRankedMappings(guildId, category)) {
            RoleTarget.fromRanked(category, mapping.getMinRank())
                    .ifPresent(target -> mappings.add(new Mapping(target, mapping.getRoleId())));
        }
        for (EnumRoleMapping mapping : roleMappingService.getEnumMappings(guildId, category)) {
            RoleTarget.fromEnum(category, mapping.getEnumValue())
                    .ifPresent(target -> mappings.add(new Mapping(target, mapping.getRoleId())));
        }
        mappings.sort(Comparator.comparing((Mapping m) -> m.target().ranked() ? m.target().rank() : 0)
                .thenComparing(m -> m.target().label()));
        return mappings;
    }

    public void add(Guild guild, Member invoker, RoleTarget target, Role role, Consumer<String> reply) {
        String guildId = guild.getId();
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            reply.accept(message(guildId, "error.missing_permission.manage_roles"));
            return;
        }
        Role currentRole = currentRoleId(guildId, target).map(guild::getRoleById).orElse(null);
        if (role != null) {
            linkExistingRole(guild, invoker, target, role, currentRole, reply);
            return;
        }
        if (currentRole != null && RoleSafety.isSafe(currentRole, guild.getSelfMember())) {
            reply.accept(message(guildId, target.successKey(), currentRole.getName(), target.successArg()));
            return;
        }
        Consumer<Throwable> failed = error -> {
            log.warn("Failed to create role {} in guild {}: {}", target.label(), guildId, error.getMessage());
            reply.accept(createFailedMessage(guildId, target.label()));
        };
        try {
            guild.createRole()
                    .setName(target.label())
                    .setPermissions(EnumSet.noneOf(Permission.class))
                    .queue(created -> onRoleCreated(guildId, target, created, currentRole, reply), failed);
        } catch (Exception e) {
            failed.accept(e);
        }
    }

    public String remove(Guild guild, RoleTarget target) {
        String guildId = guild.getId();
        Optional<String> removedRoleId = target.ranked()
                ? roleMappingService.removeRankedMapping(guildId, target.category(), target.rank())
                : roleMappingService.removeEnumMapping(guildId, target.category(), target.enumValue());
        String reply = message(guildId, target.removedKey(), target.removedArgs().toArray());
        Role removedRole = removedRoleId.map(guild::getRoleById).orElse(null);
        if (removedRole != null) {
            reply += "\n" + message(guildId, "setup.role_mapping.old_role_kept", removedRole.getName());
        }
        return reply;
    }

    private void onRoleCreated(String guildId, RoleTarget target, Role createdRole, Role previousRole,
                               Consumer<String> reply) {
        try {
            save(guildId, target, createdRole.getId(), true);
        } catch (Exception e) {
            log.error("Failed to save mapping for created role {} in guild {}", createdRole.getName(), guildId, e);
            createdRole.delete().queue(null, deleteError -> log.warn("Failed to delete orphaned role {} in guild {}: {}",
                    createdRole.getName(), guildId, deleteError.getMessage()));
            reply.accept(createFailedMessage(guildId, createdRole.getName()));
            return;
        }
        String message = message(guildId, target.successKey(), createdRole.getName(), target.successArg());
        reply.accept(withOldRoleNotice(guildId, previousRole, createdRole, message));
    }

    private void linkExistingRole(Guild guild, Member invoker, RoleTarget target, Role role, Role currentRole,
                                  Consumer<String> reply) {
        String guildId = guild.getId();
        Optional<RoleSafety.Violation> violation = RoleSafety.check(role, guild.getSelfMember(), invoker);
        if (violation.isPresent()) {
            reply.accept(message(guildId, violation.get().getMessageKey(), role.getName()));
            return;
        }
        Optional<RoleCategory> conflictingCategory =
                roleMappingService.findOtherCategoryUsingRole(guildId, role.getId(), target.category());
        if (conflictingCategory.isPresent()) {
            reply.accept(message(guildId, "error.role_already_mapped",
                    role.getName(), conflictingCategory.get().getDisplayName()));
            return;
        }
        save(guildId, target, role.getId(), false);
        String message = message(guildId, target.successKey(), role.getName(), target.successArg());
        reply.accept(withOldRoleNotice(guildId, currentRole, role, message));
    }

    private Optional<String> currentRoleId(String guildId, RoleTarget target) {
        return target.ranked()
                ? roleMappingService.findRankedMappingRoleId(guildId, target.category(), target.rank())
                : roleMappingService.findEnumMappingRoleId(guildId, target.category(), target.enumValue());
    }

    private void save(String guildId, RoleTarget target, String roleId, boolean botCreated) {
        if (target.ranked()) {
            roleMappingService.saveRankedMapping(guildId, target.category(), target.rank(), roleId, botCreated);
        } else {
            roleMappingService.saveEnumMapping(guildId, target.category(), target.enumValue(), roleId, botCreated);
        }
    }

    private String createFailedMessage(String guildId, String roleName) {
        return message(guildId, "setup.role_mapping.create_failed", roleName);
    }

    private String withOldRoleNotice(String guildId, Role previousRole, Role newRole, String message) {
        if (previousRole == null || previousRole.getId().equals(newRole.getId())) {
            return message;
        }
        return message + "\n" + message(guildId, "setup.role_mapping.old_role_kept", previousRole.getName());
    }

    private String message(String guildId, String key, Object... args) {
        return messageService.getMessage(guildId, key, args);
    }
}
