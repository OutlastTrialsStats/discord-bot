package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.backend.api.model.AccountCreationType;
import com.outlasttrialsstats.backend.api.model.ActiveReagentSkillType;
import com.outlasttrialsstats.backend.api.model.InvasionRanking;
import com.outlasttrialsstats.backend.api.model.PlatformType;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.RoleConfig;
import com.outlasttrialsstats.discordbot.feature.setup.RoleSafety;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoleMappingCommand {

    private final RoleMappingService roleMappingService;
    private final MessageService messageService;

    public void onRoleMapping(SlashCommandInteractionEvent event, String subcommand) {
        switch (subcommand) {
            case "prestige" -> onPrestige(event);
            case "level" -> onLevel(event);
            case "skill" -> onSkill(event);
            case "invasion-ranking" -> onInvasionRanking(event);
            case "total-invasion-matches" -> onTotalInvasionMatches(event);
            case "season-invasion-points" -> onSeasonInvasionPoints(event);
            case "platform" -> onPlatform(event);
            case "account-type" -> onAccountType(event);
            case "connected-account" -> onConnectedAccount(event);
        }
    }

    public void onRemoveRoleMapping(SlashCommandInteractionEvent event, String subcommand) {
        switch (subcommand) {
            case "prestige" -> onRemovePrestige(event);
            case "level" -> onRemoveLevel(event);
            case "skill" -> onRemoveSkill(event);
            case "invasion-ranking" -> onRemoveInvasionRanking(event);
            case "total-invasion-matches" -> onRemoveTotalInvasionMatches(event);
            case "season-invasion-points" -> onRemoveSeasonInvasionPoints(event);
            case "platform" -> onRemovePlatform(event);
            case "account-type" -> onRemoveAccountType(event);
            case "connected-account" -> onRemoveConnectedAccount(event);
        }
    }


    private void onPrestige(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);
        int threshold = event.getOption("threshold").getAsInt();
        Role role = getOptionalRole(event);
        String roleName = "Prestige " + threshold + "+";

        createOrLinkRole(guild, guildId, roleName, role, RoleCategory.PRESTIGE,
                roleMappingService.findRankedMappingRoleId(guildId, RoleCategory.PRESTIGE, threshold),
                (roleId, botCreated) -> roleMappingService.saveRankedMapping(guildId, RoleCategory.PRESTIGE, threshold, roleId, botCreated),
                event, "setup.prestige_role.success", threshold);
    }

    private void onRemovePrestige(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        int threshold = event.getOption("threshold").getAsInt();
        Optional<String> removedRoleId = roleMappingService.removeRankedMapping(guildId, RoleCategory.PRESTIGE, threshold);
        replyRemoved(event, guildId, removedRoleId, messageService.getMessage(guildId, "setup.prestige_role.removed", threshold));
    }


    private void onLevel(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);
        int threshold = event.getOption("threshold").getAsInt();
        Role role = getOptionalRole(event);
        String roleName = "Level " + threshold + "+";

        createOrLinkRole(guild, guildId, roleName, role, RoleCategory.LEVEL,
                roleMappingService.findRankedMappingRoleId(guildId, RoleCategory.LEVEL, threshold),
                (roleId, botCreated) -> roleMappingService.saveRankedMapping(guildId, RoleCategory.LEVEL, threshold, roleId, botCreated),
                event, "setup.level_role.success", threshold);
    }

    private void onRemoveLevel(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        int threshold = event.getOption("threshold").getAsInt();
        Optional<String> removedRoleId = roleMappingService.removeRankedMapping(guildId, RoleCategory.LEVEL, threshold);
        replyRemoved(event, guildId, removedRoleId, messageService.getMessage(guildId, "setup.level_role.removed", threshold));
    }


    private void onSkill(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);
        ActiveReagentSkillType reagentSkill = ActiveReagentSkillType.fromValue(event.getOption("skill").getAsString());
        String roleName = RoleConfig.skillName(reagentSkill);
        Role role = getOptionalRole(event);

        createOrLinkRole(guild, guildId, roleName, role, RoleCategory.REAGENT_RIG,
                roleMappingService.findEnumMappingRoleId(guildId, RoleCategory.REAGENT_RIG, reagentSkill.getValue()),
                (roleId, botCreated) -> roleMappingService.saveEnumMapping(guildId, RoleCategory.REAGENT_RIG, reagentSkill.getValue(), roleId, botCreated),
                event, "setup.skill_role.success", roleName);
    }

    private void onRemoveSkill(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        ActiveReagentSkillType reagentSkill = ActiveReagentSkillType.fromValue(event.getOption("skill").getAsString());
        Optional<String> removedRoleId = roleMappingService.removeEnumMapping(guildId, RoleCategory.REAGENT_RIG, reagentSkill.getValue());
        replyRemoved(event, guildId, removedRoleId, messageService.getMessage(guildId, "setup.skill_role.removed", RoleConfig.skillName(reagentSkill)));
    }


    private void onInvasionRanking(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);
        InvasionRanking invasionRanking = InvasionRanking.fromValue(event.getOption("ranking").getAsString());
        String roleName = RoleConfig.INVASION_RANKING_NAMES.get(invasionRanking);
        Role role = getOptionalRole(event);

        createOrLinkRole(guild, guildId, roleName, role, RoleCategory.INVASION_RANKING,
                roleMappingService.findRankedMappingRoleId(guildId, RoleCategory.INVASION_RANKING, invasionRanking.ordinal()),
                (roleId, botCreated) -> roleMappingService.saveRankedMapping(guildId, RoleCategory.INVASION_RANKING, invasionRanking.ordinal(), roleId, botCreated),
                event, "setup.role_mapping.success", roleName);
    }

    private void onRemoveInvasionRanking(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        InvasionRanking invasionRanking = InvasionRanking.fromValue(event.getOption("ranking").getAsString());
        Optional<String> removedRoleId = roleMappingService.removeRankedMapping(guildId, RoleCategory.INVASION_RANKING, invasionRanking.ordinal());
        replyRemoved(event, guildId, removedRoleId, messageService.getMessage(guildId, "setup.role_mapping.removed",
                RoleConfig.INVASION_RANKING_NAMES.get(invasionRanking)));
    }


    private void onTotalInvasionMatches(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);
        int threshold = event.getOption("threshold").getAsInt();
        Role role = getOptionalRole(event);
        String roleName = "Invasion Matches " + threshold + "+";

        createOrLinkRole(guild, guildId, roleName, role, RoleCategory.TOTAL_INVASION_MATCHES,
                roleMappingService.findRankedMappingRoleId(guildId, RoleCategory.TOTAL_INVASION_MATCHES, threshold),
                (roleId, botCreated) -> roleMappingService.saveRankedMapping(guildId, RoleCategory.TOTAL_INVASION_MATCHES, threshold, roleId, botCreated),
                event, "setup.total_invasion_matches_role.success", threshold);
    }

    private void onRemoveTotalInvasionMatches(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        int threshold = event.getOption("threshold").getAsInt();
        Optional<String> removedRoleId = roleMappingService.removeRankedMapping(guildId, RoleCategory.TOTAL_INVASION_MATCHES, threshold);
        replyRemoved(event, guildId, removedRoleId, messageService.getMessage(guildId, "setup.total_invasion_matches_role.removed", threshold));
    }


    private void onSeasonInvasionPoints(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);
        int threshold = event.getOption("threshold").getAsInt();
        Role role = getOptionalRole(event);
        String roleName = "Invasion Points " + threshold + "+";

        createOrLinkRole(guild, guildId, roleName, role, RoleCategory.SEASON_INVASION_POINTS,
                roleMappingService.findRankedMappingRoleId(guildId, RoleCategory.SEASON_INVASION_POINTS, threshold),
                (roleId, botCreated) -> roleMappingService.saveRankedMapping(guildId, RoleCategory.SEASON_INVASION_POINTS, threshold, roleId, botCreated),
                event, "setup.season_invasion_points_role.success", threshold);
    }

    private void onRemoveSeasonInvasionPoints(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        int threshold = event.getOption("threshold").getAsInt();
        Optional<String> removedRoleId = roleMappingService.removeRankedMapping(guildId, RoleCategory.SEASON_INVASION_POINTS, threshold);
        replyRemoved(event, guildId, removedRoleId, messageService.getMessage(guildId, "setup.season_invasion_points_role.removed", threshold));
    }


    private void onPlatform(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);
        PlatformType platformType = PlatformType.fromValue(event.getOption("platform").getAsString());
        String roleName = RoleConfig.PLATFORM_NAMES.get(platformType);
        Role role = getOptionalRole(event);

        createOrLinkRole(guild, guildId, roleName, role, RoleCategory.PLATFORM,
                roleMappingService.findEnumMappingRoleId(guildId, RoleCategory.PLATFORM, platformType.getValue()),
                (roleId, botCreated) -> roleMappingService.saveEnumMapping(guildId, RoleCategory.PLATFORM, platformType.getValue(), roleId, botCreated),
                event, "setup.role_mapping.success", roleName);
    }

    private void onRemovePlatform(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        PlatformType platformType = PlatformType.fromValue(event.getOption("platform").getAsString());
        Optional<String> removedRoleId = roleMappingService.removeEnumMapping(guildId, RoleCategory.PLATFORM, platformType.getValue());
        replyRemoved(event, guildId, removedRoleId, messageService.getMessage(guildId, "setup.role_mapping.removed",
                RoleConfig.PLATFORM_NAMES.get(platformType)));
    }


    private void onAccountType(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);
        AccountCreationType type = AccountCreationType.fromValue(event.getOption("account-type").getAsString());
        String roleName = RoleConfig.ACCOUNT_TYPE_NAMES.get(type);
        Role role = getOptionalRole(event);

        createOrLinkRole(guild, guildId, roleName, role, RoleCategory.ACCOUNT_TYPE,
                roleMappingService.findEnumMappingRoleId(guildId, RoleCategory.ACCOUNT_TYPE, type.getValue()),
                (roleId, botCreated) -> roleMappingService.saveEnumMapping(guildId, RoleCategory.ACCOUNT_TYPE, type.getValue(), roleId, botCreated),
                event, "setup.role_mapping.success", roleName);
    }

    private void onRemoveAccountType(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        AccountCreationType type = AccountCreationType.fromValue(event.getOption("account-type").getAsString());
        Optional<String> removedRoleId = roleMappingService.removeEnumMapping(guildId, RoleCategory.ACCOUNT_TYPE, type.getValue());
        replyRemoved(event, guildId, removedRoleId, messageService.getMessage(guildId, "setup.role_mapping.removed",
                RoleConfig.ACCOUNT_TYPE_NAMES.get(type)));
    }


    private void onConnectedAccount(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);
        String roleName = "Account Connected";
        Role role = getOptionalRole(event);

        createOrLinkRole(guild, guildId, roleName, role, RoleCategory.CONNECTED_ACCOUNT,
                roleMappingService.findEnumMappingRoleId(guildId, RoleCategory.CONNECTED_ACCOUNT,
                        RoleConfig.CONNECTED_ACCOUNT_VALUE),
                (roleId, botCreated) -> roleMappingService.saveEnumMapping(guildId, RoleCategory.CONNECTED_ACCOUNT,
                        RoleConfig.CONNECTED_ACCOUNT_VALUE, roleId, botCreated),
                event, "setup.connected_account_role.success", roleName);
    }

    private void onRemoveConnectedAccount(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Optional<String> removedRoleId = roleMappingService.removeEnumMapping(guildId, RoleCategory.CONNECTED_ACCOUNT,
                RoleConfig.CONNECTED_ACCOUNT_VALUE);
        replyRemoved(event, guildId, removedRoleId, messageService.getMessage(guildId, "setup.connected_account_role.removed"));
    }


    private Role getOptionalRole(SlashCommandInteractionEvent event) {
        OptionMapping roleOption = event.getOption("role");
        return roleOption != null ? roleOption.getAsRole() : null;
    }

    private void createOrLinkRole(Guild guild, String guildId, String roleName, Role role, RoleCategory category,
                                  Optional<String> currentRoleId, BiConsumer<String, Boolean> saveMapping,
                                  SlashCommandInteractionEvent event, String messageKey, Object extraArg) {
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            replyEphemeral(event, messageService.getMessage(guildId, "error.missing_permission.manage_roles"));
            return;
        }
        Role currentRole = currentRoleId.map(guild::getRoleById).orElse(null);
        if (role != null) {
            linkExistingRole(guild, guildId, role, currentRole, category, saveMapping, event, messageKey, extraArg);
            return;
        }
        if (currentRole != null && RoleSafety.isSafe(currentRole, guild.getSelfMember())) {
            replyEphemeral(event, messageService.getMessage(guildId, messageKey, currentRole.getName(), extraArg));
            return;
        }
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();
        try {
            guild.createRole()
                    .setName(roleName)
                    .setPermissions(EnumSet.noneOf(Permission.class))
                    .queue(createdRole -> onRoleCreated(guildId, createdRole, currentRole, saveMapping, hook,
                                    messageKey, extraArg),
                            error -> {
                                log.warn("Failed to create role {} in guild {}: {}", roleName, guildId,
                                        error.getMessage());
                                hook.editOriginal(createFailedMessage(guildId, roleName)).queue();
                            });
        } catch (Exception e) {
            log.warn("Failed to create role {} in guild {}: {}", roleName, guildId, e.getMessage());
            hook.editOriginal(createFailedMessage(guildId, roleName)).queue();
        }
    }

    private void onRoleCreated(String guildId, Role createdRole, Role previousRole,
                               BiConsumer<String, Boolean> saveMapping, InteractionHook hook,
                               String messageKey, Object extraArg) {
        try {
            saveMapping.accept(createdRole.getId(), true);
        } catch (Exception e) {
            log.error("Failed to save mapping for created role {} in guild {}", createdRole.getName(), guildId, e);
            createdRole.delete().queue(null, deleteError -> log.warn("Failed to delete orphaned role {} in guild {}: {}",
                    createdRole.getName(), guildId, deleteError.getMessage()));
            hook.editOriginal(createFailedMessage(guildId, createdRole.getName())).queue();
            return;
        }
        String message = messageService.getMessage(guildId, messageKey, createdRole.getName(), extraArg);
        hook.editOriginal(withOldRoleNotice(guildId, previousRole, createdRole, message)).queue();
    }

    private void linkExistingRole(Guild guild, String guildId, Role role, Role currentRole, RoleCategory category,
                                  BiConsumer<String, Boolean> saveMapping,
                                  SlashCommandInteractionEvent event, String messageKey, Object extraArg) {
        Optional<RoleSafety.Violation> violation = RoleSafety.check(role, guild.getSelfMember(), event.getMember());
        if (violation.isPresent()) {
            replyEphemeral(event, messageService.getMessage(guildId, violation.get().getMessageKey(), role.getName()));
            return;
        }

        Optional<RoleCategory> conflictingCategory =
                roleMappingService.findOtherCategoryUsingRole(guildId, role.getId(), category);
        if (conflictingCategory.isPresent()) {
            replyEphemeral(event, messageService.getMessage(guildId, "error.role_already_mapped",
                    role.getName(), conflictingCategory.get().getDisplayName()));
            return;
        }

        saveMapping.accept(role.getId(), false);
        String message = messageService.getMessage(guildId, messageKey, role.getName(), extraArg);
        replyEphemeral(event, withOldRoleNotice(guildId, currentRole, role, message));
    }

    private String createFailedMessage(String guildId, String roleName) {
        return messageService.getMessage(guildId, "setup.role_mapping.create_failed", roleName);
    }

    private String withOldRoleNotice(String guildId, Role previousRole, Role newRole, String message) {
        if (previousRole == null || previousRole.getId().equals(newRole.getId())) {
            return message;
        }
        return message + "\n" + messageService.getMessage(guildId, "setup.role_mapping.old_role_kept",
                previousRole.getName());
    }

    private void replyRemoved(SlashCommandInteractionEvent event, String guildId, Optional<String> removedRoleId,
                              String message) {
        Role removedRole = removedRoleId.map(event.getGuild()::getRoleById).orElse(null);
        if (removedRole != null) {
            message += "\n" + messageService.getMessage(guildId, "setup.role_mapping.old_role_kept",
                    removedRole.getName());
        }
        replyEphemeral(event, message);
    }

    private void replyEphemeral(SlashCommandInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }
}
