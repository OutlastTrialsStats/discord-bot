package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.backend.api.model.AccountCreationType;
import com.outlasttrialsstats.backend.api.model.ActiveReagentSkillType;
import com.outlasttrialsstats.backend.api.model.InvasionRanking;
import com.outlasttrialsstats.backend.api.model.PlatformType;
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
public class RoleMappingCommand {

    private final RoleMappingService roleMappingService;
    private final MessageService messageService;

    // --- Prestige ---

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command("setup role-mapping prestige")
    public void onPrestige(CommandEvent event,
                           @Param("Minimum prestige level (e.g. 10 for Prestige 10+)") int threshold,
                           @Param(value = "Existing role to use (auto-creates if not provided)", optional = true) Optional<Role> role) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        String roleName = "Prestige " + threshold + "+";

        createOrLinkRole(guild, guildId, roleName, role,
                roleId -> roleMappingService.saveRankedMapping(guildId, RoleCategory.PRESTIGE, threshold, roleId),
                event, "setup.prestige_role.success", threshold);
    }

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command("setup remove-role-mapping prestige")
    public void onRemovePrestige(CommandEvent event,
                                 @Param("Prestige threshold to remove") int threshold) {
        String guildId = event.getGuild().getId();
        roleMappingService.removeRankedMapping(guildId, RoleCategory.PRESTIGE, threshold);
        event.with().ephemeral(true)
                .reply(messageService.getMessage(guildId, "setup.prestige_role.removed", threshold));
    }

    // --- Skill (Reagent Rig) ---

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command("setup role-mapping skill")
    public void onSkill(CommandEvent event,
                        @Choices({"STUN", "XRAY", "MINE", "DOOR_BLOCKER", "HACKER", "HEAL"})
                        @Param("Reagent skill") String skill,
                        @Param(value = "Existing role to use (auto-creates if not provided)", optional = true) Optional<Role> role) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        ActiveReagentSkillType reagentSkill = ActiveReagentSkillType.fromValue(skill);
        String roleName = RoleConfig.skillName(reagentSkill);

        createOrLinkRole(guild, guildId, roleName, role,
                roleId -> roleMappingService.saveEnumMapping(guildId, RoleCategory.REAGENT_RIG, reagentSkill.getValue(), roleId),
                event, "setup.skill_role.success", roleName);
    }

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command("setup remove-role-mapping skill")
    public void onRemoveSkill(CommandEvent event,
                              @Choices({"STUN", "XRAY", "MINE", "DOOR_BLOCKER", "HACKER", "HEAL"})
                              @Param("Reagent skill") String skill) {
        String guildId = event.getGuild().getId();
        ActiveReagentSkillType reagentSkill = ActiveReagentSkillType.fromValue(skill);
        roleMappingService.removeEnumMapping(guildId, RoleCategory.REAGENT_RIG, reagentSkill.getValue());
        event.with().ephemeral(true)
                .reply(messageService.getMessage(guildId, "setup.skill_role.removed", RoleConfig.skillName(reagentSkill)));
    }

    // --- Invasion Ranking ---

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command("setup role-mapping invasion-ranking")
    public void onInvasionRanking(CommandEvent event,
                                  @Choices({"UNRANKED", "INITIATE_3", "INITIATE_2", "INITIATE_1",
                                          "BRONZE_3", "BRONZE_2", "BRONZE_1",
                                          "SILVER_3", "SILVER_2", "SILVER_1",
                                          "GOLD_3", "GOLD_2", "GOLD_1"})
                                  @Param("Invasion ranking") String ranking,
                                  @Param(value = "Existing role to use (auto-creates if not provided)", optional = true) Optional<Role> role) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        InvasionRanking invasionRanking = InvasionRanking.fromValue(ranking);
        String roleName = RoleConfig.INVASION_RANKING_NAMES.get(invasionRanking);

        createOrLinkRole(guild, guildId, roleName, role,
                roleId -> roleMappingService.saveRankedMapping(guildId, RoleCategory.INVASION_RANKING, invasionRanking.ordinal(), roleId),
                event, "setup.role_mapping.success", roleName);
    }

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command("setup remove-role-mapping invasion-ranking")
    public void onRemoveInvasionRanking(CommandEvent event,
                                        @Choices({"UNRANKED", "INITIATE_3", "INITIATE_2", "INITIATE_1",
                                                "BRONZE_3", "BRONZE_2", "BRONZE_1",
                                                "SILVER_3", "SILVER_2", "SILVER_1",
                                                "GOLD_3", "GOLD_2", "GOLD_1"})
                                        @Param("Invasion ranking") String ranking) {
        String guildId = event.getGuild().getId();
        InvasionRanking invasionRanking = InvasionRanking.fromValue(ranking);
        roleMappingService.removeRankedMapping(guildId, RoleCategory.INVASION_RANKING, invasionRanking.ordinal());
        event.with().ephemeral(true)
                .reply(messageService.getMessage(guildId, "setup.role_mapping.removed",
                        RoleConfig.INVASION_RANKING_NAMES.get(invasionRanking)));
    }

    // --- Platform ---

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command("setup role-mapping platform")
    public void onPlatform(CommandEvent event,
                           @Choices({"STEAM", "PLAYSTATION", "XBOX", "EPIC_GAMES"})
                           @Param("Gaming platform") String platform,
                           @Param(value = "Existing role to use (auto-creates if not provided)", optional = true) Optional<Role> role) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        PlatformType platformType = PlatformType.fromValue(platform);
        String roleName = RoleConfig.PLATFORM_NAMES.get(platformType);

        createOrLinkRole(guild, guildId, roleName, role,
                roleId -> roleMappingService.saveEnumMapping(guildId, RoleCategory.PLATFORM, platformType.getValue(), roleId),
                event, "setup.role_mapping.success", roleName);
    }

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command("setup remove-role-mapping platform")
    public void onRemovePlatform(CommandEvent event,
                                 @Choices({"STEAM", "PLAYSTATION", "XBOX", "EPIC_GAMES"})
                                 @Param("Gaming platform") String platform) {
        String guildId = event.getGuild().getId();
        PlatformType platformType = PlatformType.fromValue(platform);
        roleMappingService.removeEnumMapping(guildId, RoleCategory.PLATFORM, platformType.getValue());
        event.with().ephemeral(true)
                .reply(messageService.getMessage(guildId, "setup.role_mapping.removed",
                        RoleConfig.PLATFORM_NAMES.get(platformType)));
    }

    // --- Account Type ---

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command("setup role-mapping account-type")
    public void onAccountType(CommandEvent event,
                              @Choices({"CLOSED_BETA_USER", "EARLY_ACCESS_USER"})
                              @Param("Account type") String accountType,
                              @Param(value = "Existing role to use (auto-creates if not provided)", optional = true) Optional<Role> role) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        AccountCreationType type = AccountCreationType.fromValue(accountType);
        String roleName = RoleConfig.ACCOUNT_TYPE_NAMES.get(type);

        createOrLinkRole(guild, guildId, roleName, role,
                roleId -> roleMappingService.saveEnumMapping(guildId, RoleCategory.ACCOUNT_TYPE, type.getValue(), roleId),
                event, "setup.role_mapping.success", roleName);
    }

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command("setup remove-role-mapping account-type")
    public void onRemoveAccountType(CommandEvent event,
                                    @Choices({"CLOSED_BETA_USER", "EARLY_ACCESS_USER"})
                                    @Param("Account type") String accountType) {
        String guildId = event.getGuild().getId();
        AccountCreationType type = AccountCreationType.fromValue(accountType);
        roleMappingService.removeEnumMapping(guildId, RoleCategory.ACCOUNT_TYPE, type.getValue());
        event.with().ephemeral(true)
                .reply(messageService.getMessage(guildId, "setup.role_mapping.removed",
                        RoleConfig.ACCOUNT_TYPE_NAMES.get(type)));
    }

    // --- Helper ---

    private void createOrLinkRole(Guild guild, String guildId, String roleName, Optional<Role> role,
                                  java.util.function.Consumer<String> saveMapping,
                                  CommandEvent event, String messageKey, Object extraArg) {
        if (role.isPresent()) {
            Role existingRole = role.get();
            saveMapping.accept(existingRole.getId());
            event.with().ephemeral(true)
                    .reply(messageService.getMessage(guildId, messageKey, existingRole.getName(), extraArg));
        } else {
            guild.createRole()
                    .setName(roleName)
                    .setPermissions(EnumSet.noneOf(Permission.class))
                    .queue(createdRole -> {
                        saveMapping.accept(createdRole.getId());
                        event.with().ephemeral(true)
                                .reply(messageService.getMessage(guildId, messageKey, createdRole.getName(), extraArg));
                    });
        }
    }
}
