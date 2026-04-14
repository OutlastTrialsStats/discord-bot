package com.outlasttrialsstats.discordbot.feature.profile.service;

import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import com.outlasttrialsstats.discordbot.entity.EnumRoleMapping;
import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.entity.RankedRoleMapping;
import com.outlasttrialsstats.discordbot.feature.profile.dto.RoleAssignmentResult;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.RoleConfig;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleAssignmentService {

    private final TOTStatsApiClient statsApiClient;
    private final RoleMappingService roleMappingService;
    private final GuildServerRepository guildServerRepository;

    public RoleAssignmentResult assignRoles(Guild guild, Member member) {
        log.debug("Assigning roles for member {} in guild {}", member.getId(), guild.getId());

        var profileOpt = statsApiClient.getProfile(member.getId());
        if (profileOpt.isEmpty()) {
            log.debug("Member {} is not verified, skipping", member.getId());
            cleanupUnverifiedMember(guild, member);
            return RoleAssignmentResult.notVerified();
        }

        return assignRolesFromProfile(guild, member, profileOpt.get());
    }

    public void cleanupUnverifiedMember(Guild guild, Member member) {
        assignEnumRole(guild, member, guild.getId(), RoleCategory.CONNECTED_ACCOUNT,
                null, new ArrayList<>(), new ArrayList<>());
    }

    public RoleAssignmentResult assignRolesFromProfile(Guild guild, Member member, DiscordProfileResponse profile) {
        String guildId = guild.getId();
        log.debug("Profile data for member {} in guild {}: {}", member.getId(), guildId, profile);
        List<String> addedRoles = new ArrayList<>();
        List<String> removedRoles = new ArrayList<>();

        if (profile.getPrestigeLevel() != null) {
            assignRankedRole(guild, member, guildId, RoleCategory.PRESTIGE,
                    profile.getPrestigeLevel(), addedRoles, removedRoles);
        } else {
            log.debug("Member {}: skipping PRESTIGE — value is null", member.getId());
        }

        if (profile.getLevel() != null) {
            assignRankedRole(guild, member, guildId, RoleCategory.LEVEL,
                    profile.getLevel(), addedRoles, removedRoles);
        } else {
            log.debug("Member {}: skipping LEVEL — value is null", member.getId());
        }

        if (profile.getInvasionRanking() != null) {
            assignRankedRole(guild, member, guildId, RoleCategory.INVASION_RANKING,
                    profile.getInvasionRanking().ordinal(), addedRoles, removedRoles);
        } else {
            log.debug("Member {}: skipping INVASION_RANKING — value is null", member.getId());
        }

        if (profile.getTotalInvasionMatchesPlayed() != null) {
            assignRankedRole(guild, member, guildId, RoleCategory.TOTAL_INVASION_MATCHES,
                    profile.getTotalInvasionMatchesPlayed(), addedRoles, removedRoles);
        } else {
            log.debug("Member {}: skipping TOTAL_INVASION_MATCHES — value is null", member.getId());
        }

        if (profile.getSeasonTotalInvasionPoints() != null) {
            assignRankedRole(guild, member, guildId, RoleCategory.SEASON_INVASION_POINTS,
                    profile.getSeasonTotalInvasionPoints(), addedRoles, removedRoles);
        } else {
            log.debug("Member {}: skipping SEASON_INVASION_POINTS — value is null", member.getId());
        }

        if (profile.getActiveReagentSkill() != null) {
            assignEnumRole(guild, member, guildId, RoleCategory.REAGENT_RIG,
                    profile.getActiveReagentSkill().getValue(), addedRoles, removedRoles);
        } else {
            log.debug("Member {}: skipping REAGENT_RIG — value is null", member.getId());
        }

        if (profile.getPlatformType() != null) {
            assignEnumRole(guild, member, guildId, RoleCategory.PLATFORM,
                    profile.getPlatformType().getValue(), addedRoles, removedRoles);
        } else {
            log.debug("Member {}: skipping PLATFORM — value is null", member.getId());
        }

        if (profile.getAccountCreationType() != null) {
            assignEnumRole(guild, member, guildId, RoleCategory.ACCOUNT_TYPE,
                    profile.getAccountCreationType().getValue(), addedRoles, removedRoles);
        } else {
            log.debug("Member {}: skipping ACCOUNT_TYPE — value is null", member.getId());
        }

        assignEnumRole(guild, member, guildId, RoleCategory.CONNECTED_ACCOUNT,
                RoleConfig.CONNECTED_ACCOUNT_VALUE, addedRoles, removedRoles);

        if (!addedRoles.isEmpty() || !removedRoles.isEmpty()) {
            log.info("Member {} in guild {}: added [{}], removed [{}]",
                    member.getId(), guildId, String.join(", ", addedRoles), String.join(", ", removedRoles));
        }

        updateNicknameIfEnabled(guild, member, profile);

        return RoleAssignmentResult.of(addedRoles, removedRoles);
    }

    private void updateNicknameIfEnabled(Guild guild, Member member, DiscordProfileResponse profile) {
        String displayName = profile.getDisplayName();
        if (displayName == null) return;

        boolean autoNickname = guildServerRepository.findById(guild.getId())
                .map(GuildServer::isAutoNickname)
                .orElse(false);

        if (!autoNickname) return;

        if (!displayName.equals(member.getEffectiveName())) {
            guild.modifyNickname(member, displayName).queue(
                    _ -> log.debug("Updated nickname for {} to '{}'", member.getId(), displayName),
                    error -> log.warn("Failed to update nickname for {}: {}", member.getId(), error.getMessage())
            );
        }
    }

    private void assignRankedRole(Guild guild, Member member, String guildId,
                                  RoleCategory category, int currentRank,
                                  List<String> addedRoles, List<String> removedRoles) {
        var allMappings = roleMappingService.getRankedMappings(guildId, category);
        if (allMappings.isEmpty()) {
            log.debug("No ranked role mappings configured for category {} in guild {}", category, guildId);
            return;
        }

        var bestMapping = roleMappingService.getBestRankedMapping(guildId, category, currentRank);
        if (bestMapping.isEmpty()) {
            log.debug("Member {}: no matching ranked mapping for {} with value {}",
                    member.getId(), category, currentRank);
        }
        Set<String> targetRoleIds = bestMapping
                .map(m -> Set.of(m.getRoleId()))
                .orElse(Set.of());

        syncRoles(guild, member,
                allMappings.stream().map(RankedRoleMapping::getRoleId).toList(),
                targetRoleIds, addedRoles, removedRoles);
    }

    private void assignEnumRole(Guild guild, Member member, String guildId,
                                RoleCategory category, String currentValue,
                                List<String> addedRoles, List<String> removedRoles) {
        var allMappings = roleMappingService.getEnumMappings(guildId, category);
        if (allMappings.isEmpty()) {
            log.debug("No enum role mappings configured for category {} in guild {}", category, guildId);
            return;
        }

        Set<String> targetRoleIds = allMappings.stream()
                .filter(m -> m.getEnumValue().equals(currentValue))
                .map(EnumRoleMapping::getRoleId)
                .collect(Collectors.toSet());

        if (targetRoleIds.isEmpty()) {
            log.debug("Member {}: no matching enum mapping for {} with value '{}'",
                    member.getId(), category, currentValue);
        }

        syncRoles(guild, member,
                allMappings.stream().map(EnumRoleMapping::getRoleId).toList(),
                targetRoleIds, addedRoles, removedRoles);
    }

    private void syncRoles(Guild guild, Member member, List<String> allRoleIds,
                           Set<String> targetRoleIds, List<String> addedRoles, List<String> removedRoles) {
        for (String roleId : allRoleIds) {
            Role role = guild.getRoleById(roleId);
            if (role == null) {
                log.debug("Role {} not found in guild {} — mapping is stale", roleId, guild.getId());
                continue;
            }

            boolean hasRole = member.getRoles().contains(role);
            boolean shouldHaveRole = targetRoleIds.contains(roleId);

            try {
                if (shouldHaveRole && !hasRole) {
                    guild.addRoleToMember(member, role).queue(
                            _ -> log.debug("Added role '{}' to member {} in guild {}", role.getName(), member.getId(), guild.getId()),
                            error -> log.warn("Failed to add role '{}' to member {} in guild {}: {}", role.getName(), member.getId(), guild.getId(), error.getMessage())
                    );
                    addedRoles.add(role.getName());
                } else if (!shouldHaveRole && hasRole) {
                    guild.removeRoleFromMember(member, role).queue(
                            _ -> log.debug("Removed role '{}' from member {} in guild {}", role.getName(), member.getId(), guild.getId()),
                            error -> log.warn("Failed to remove role '{}' from member {} in guild {}: {}", role.getName(), member.getId(), guild.getId(), error.getMessage())
                    );
                    removedRoles.add(role.getName());
                }
            } catch (Exception e) {
                log.warn("Cannot modify role '{}' for member {} in guild {}: {}",
                        role.getName(), member.getId(), guild.getId(), e.getMessage());
            }
        }
    }

}
