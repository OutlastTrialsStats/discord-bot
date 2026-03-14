package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.backend.api.model.ActiveReagentSkillType;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.RoleConfig;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import io.github.kaktushose.jdac.annotations.interactions.Choices;
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
public class SkillRoleCommand {

    private final RoleMappingService roleMappingService;
    private final MessageService messageService;

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command("setup skill-role")
    public void onSkillRole(CommandEvent event,
                            @Choices({"STUN", "XRAY", "MINE", "DOOR_BLOCKER", "HACKER", "HEAL"})
                            @Param("Reagent skill") String skill,
                            @Param(value = "Existing role to use (auto-creates if not provided)", optional = true) Optional<Role> role) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        ActiveReagentSkillType reagentSkill = ActiveReagentSkillType.fromValue(skill);

        if (role.isPresent()) {
            Role existingRole = role.get();
            roleMappingService.saveEnumMapping(guildId, RoleCategory.REAGENT_RIG, reagentSkill.getValue(), existingRole.getId());
            event.with().ephemeral(true)
                    .reply(messageService.getMessage(guildId, "setup.skill_role.success",
                            existingRole.getName(), RoleConfig.skillName(reagentSkill)));
        } else {
            String roleName = RoleConfig.skillName(reagentSkill);
            guild.createRole()
                    .setName(roleName)
                    .setPermissions(EnumSet.noneOf(Permission.class))
                    .queue(createdRole -> {
                        roleMappingService.saveEnumMapping(guildId, RoleCategory.REAGENT_RIG, reagentSkill.getValue(), createdRole.getId());
                        event.with().ephemeral(true)
                                .reply(messageService.getMessage(guildId, "setup.skill_role.success",
                                        createdRole.getName(), RoleConfig.skillName(reagentSkill)));
                    });
        }
    }

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command("setup remove-skill-role")
    public void onRemoveSkillRole(CommandEvent event,
                                  @Choices({"STUN", "XRAY", "MINE", "DOOR_BLOCKER", "HACKER", "HEAL"})
                                  @Param("Reagent skill") String skill) {
        String guildId = event.getGuild().getId();
        ActiveReagentSkillType reagentSkill = ActiveReagentSkillType.fromValue(skill);
        roleMappingService.removeEnumMapping(guildId, RoleCategory.REAGENT_RIG, reagentSkill.getValue());
        event.with().ephemeral(true)
                .reply(messageService.getMessage(guildId, "setup.skill_role.removed",
                        RoleConfig.skillName(reagentSkill)));
    }
}
