package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import io.github.kaktushose.jdac.annotations.interactions.Command;
import io.github.kaktushose.jdac.annotations.interactions.CommandConfig;
import io.github.kaktushose.jdac.annotations.interactions.Interaction;
import io.github.kaktushose.jdac.annotations.interactions.Param;
import io.github.kaktushose.jdac.dispatching.events.interactions.CommandEvent;
import java.util.EnumSet;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.stereotype.Component;

@Interaction
@Component
@RequiredArgsConstructor
public class PrestigeRoleCommand {

    private final RoleMappingService roleMappingService;
    private final MessageService messageService;

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command(value = "setup prestige-role", desc = "Link a Discord role to a prestige threshold (auto-creates if not provided)")
    public void onPrestigeRole(CommandEvent event,
                               @Param("Minimum prestige level (e.g. 10 for Prestige 10+)") int threshold,
                               @Param(value = "Existing role to use (auto-creates if not provided)", optional = true) Optional<Role> role) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();

        if (role.isPresent()) {
            Role existingRole = role.get();
            roleMappingService.savePrestigeMapping(guildId, threshold, existingRole.getId());
            event.with().ephemeral(true)
                    .reply(messageService.getMessage(guildId, "setup.prestige_role.success",
                            existingRole.getName(), threshold));
        } else {
            guild.createRole()
                    .setName("Prestige " + threshold + "+")
                    .setPermissions(EnumSet.noneOf(Permission.class))
                    .queue(createdRole -> {
                        roleMappingService.savePrestigeMapping(guildId, threshold, createdRole.getId());
                        event.with().ephemeral(true)
                                .reply(messageService.getMessage(guildId, "setup.prestige_role.success",
                                        createdRole.getName(), threshold));
                    });
        }
    }

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command(value = "setup remove-prestige-role", desc = "Remove a prestige role mapping from this server")
    public void onRemovePrestigeRole(CommandEvent event,
                                     @Param("Prestige threshold to remove") int threshold) {
        String guildId = event.getGuild().getId();
        roleMappingService.removePrestigeMapping(guildId, threshold);
        event.with().ephemeral(true)
                .reply(messageService.getMessage(guildId, "setup.prestige_role.removed", threshold));
    }
}
