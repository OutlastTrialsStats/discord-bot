package com.outlasttrialsstats.discordbot.feature.profile.service;

import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import com.outlasttrialsstats.discordbot.entity.EnumRoleMapping;
import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.entity.RankedRoleMapping;
import com.outlasttrialsstats.discordbot.feature.profile.dto.RoleAssignmentResult;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.RoleConfig;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService.MappedRole;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import com.outlasttrialsstats.discordbot.shared.ProfileLookup;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
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

        return switch (statsApiClient.getProfile(member.getId())) {
            case ProfileLookup.Found(var profile) -> assignRolesFromProfile(guild, member, profile);
            case ProfileLookup.NotLinked() -> {
                log.debug("Member {} is not linked, removing bot-managed roles", member.getId());
                yield cleanupUnverifiedMember(guild, member);
            }
            case ProfileLookup.Failed(var reason) -> {
                log.warn("Profile lookup for member {} in guild {} failed ({}), leaving roles untouched",
                        member.getId(), guild.getId(), reason);
                yield RoleAssignmentResult.lookupFailed();
            }
        };
    }

    public RoleAssignmentResult cleanupUnverifiedMember(Guild guild, Member member) {
        return cleanupUnverifiedMember(guild, member, getMappedRoleIds(guild.getId()));
    }

    public RoleAssignmentResult cleanupUnverifiedMember(Guild guild, Member member,
                                                        Collection<String> mappedRoleIds) {
        List<String> removedRoles = new ArrayList<>();
        syncRoles(guild, member, mappedRoleIds, Set.of(), new ArrayList<>(), removedRoles);

        if (!removedRoles.isEmpty()) {
            log.info("Member {} in guild {} is not linked: removed [{}]",
                    member.getId(), guild.getId(), String.join(", ", removedRoles));
        }
        return RoleAssignmentResult.notVerified(removedRoles);
    }

    public List<String> getMappedRoleIds(String guildId) {
        return roleMappingService.getMappedRoles(guildId).stream()
                .map(MappedRole::roleId)
                .toList();
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
        String nickname = toNickname(profile.getDisplayName());
        if (nickname == null) return;

        if (nickname.equals(member.getEffectiveName())) return;

        boolean autoNickname = guildServerRepository.findById(guild.getId())
                .map(GuildServer::isAutoNickname)
                .orElse(false);

        if (!autoNickname) return;

        if (member.isOwner()) {
            log.debug("Not updating nickname for {} in guild {}: member is the guild owner",
                    member.getId(), guild.getId());
            return;
        }

        Member selfMember = guild.getSelfMember();
        if (!selfMember.hasPermission(Permission.NICKNAME_MANAGE)) {
            log.debug("Not updating nickname for {} in guild {}: missing NICKNAME_MANAGE permission",
                    member.getId(), guild.getId());
            return;
        }
        if (!selfMember.canInteract(member)) {
            log.debug("Not updating nickname for {} in guild {}: member is above the bot in the role hierarchy",
                    member.getId(), guild.getId());
            return;
        }

        try {
            guild.modifyNickname(member, nickname).queue(
                    _ -> log.debug("Updated nickname for {} to '{}'", member.getId(), nickname),
                    error -> log.warn("Failed to update nickname for {}: {}", member.getId(), error.getMessage())
            );
        } catch (Exception e) {
            log.warn("Cannot update nickname for {} in guild {}: {}", member.getId(), guild.getId(), e.getMessage());
        }
    }

    static String toNickname(String displayName) {
        if (displayName == null) return null;
        String stripped = displayName.strip();
        if (stripped.isEmpty()) return null;
        if (stripped.codePointCount(0, stripped.length()) <= Member.MAX_NICKNAME_LENGTH) {
            return stripped;
        }
        String truncated = stripped.substring(0, stripped.offsetByCodePoints(0, Member.MAX_NICKNAME_LENGTH)).strip();
        return truncated.isEmpty() ? null : truncated;
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

    private void syncRoles(Guild guild, Member member, Collection<String> allRoleIds,
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
