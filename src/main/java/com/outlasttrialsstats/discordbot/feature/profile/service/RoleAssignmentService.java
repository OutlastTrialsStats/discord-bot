package com.outlasttrialsstats.discordbot.feature.profile.service;

import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import com.outlasttrialsstats.backend.api.model.InvasionRanking;
import com.outlasttrialsstats.discordbot.entity.EnumRoleMapping;
import com.outlasttrialsstats.discordbot.entity.RankedRoleMapping;
import com.outlasttrialsstats.discordbot.feature.profile.dto.RoleAssignmentResult;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.service.BasicSetupService;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleAssignmentService {

    private final TOTStatsApiClient statsApiClient;
    private final RoleMappingService roleMappingService;

    public RoleAssignmentResult assignRoles(Guild guild, Member member) {
        log.debug("Assigning roles for member {} in guild {}", member.getId(), guild.getId());

        var profileOpt = statsApiClient.getProfile(member.getId());
        if (profileOpt.isEmpty()) {
            log.debug("Member {} is not verified, skipping", member.getId());
            return RoleAssignmentResult.notVerified();
        }

        DiscordProfileResponse profile = profileOpt.get();
        String guildId = guild.getId();
        List<String> addedRoles = new ArrayList<>();
        List<String> removedRoles = new ArrayList<>();

        log.debug("Member {} has prestige {}, skill {}, invasion ranking {}, platform {}, account type {}",
                member.getId(), profile.getPrestigeLevel(), profile.getActiveReagentSkill(),
                profile.getInvasionRanking(), profile.getPlatformType(), profile.getAccountCreationType());

        // Ranked roles (best match)
        assignRankedRole(guild, member, guildId, RoleCategory.PRESTIGE,
                profile.getPrestigeLevel() != null ? profile.getPrestigeLevel() : 0,
                addedRoles, removedRoles);

        assignRankedRole(guild, member, guildId, RoleCategory.INVASION_RANKING,
                profile.getInvasionRanking() != null ? BasicSetupService.invasionRankingToOrdinal(profile.getInvasionRanking()) : -1,
                addedRoles, removedRoles);

        // Enum roles (exact match)
        assignEnumRole(guild, member, guildId, RoleCategory.REAGENT_RIG,
                profile.getActiveReagentSkill() != null ? profile.getActiveReagentSkill().getValue() : null,
                addedRoles, removedRoles);

        assignEnumRole(guild, member, guildId, RoleCategory.PLATFORM,
                profile.getPlatformType() != null ? profile.getPlatformType().getValue() : null,
                addedRoles, removedRoles);

        assignEnumRole(guild, member, guildId, RoleCategory.ACCOUNT_TYPE,
                profile.getAccountCreationType() != null ? profile.getAccountCreationType().getValue() : null,
                addedRoles, removedRoles);

        if (!addedRoles.isEmpty() || !removedRoles.isEmpty()) {
            log.info("Member {} in guild {}: added [{}], removed [{}]",
                    member.getId(), guildId, String.join(", ", addedRoles), String.join(", ", removedRoles));
        }

        return RoleAssignmentResult.of(addedRoles, removedRoles);
    }

    private void assignRankedRole(Guild guild, Member member, String guildId,
                                  RoleCategory category, int currentRank,
                                  List<String> addedRoles, List<String> removedRoles) {
        List<RankedRoleMapping> allMappings = roleMappingService.getRankedMappings(guildId, category);
        if (allMappings.isEmpty()) {
            return;
        }

        String bestRoleId = roleMappingService.getBestRankedMapping(guildId, category, currentRank)
                .map(RankedRoleMapping::getRoleId)
                .orElse(null);

        for (var mapping : allMappings) {
            Role role = guild.getRoleById(mapping.getRoleId());
            if (role == null) {
                continue;
            }

            boolean hasRole = member.getRoles().contains(role);
            boolean shouldHaveRole = mapping.getRoleId().equals(bestRoleId);

            if (shouldHaveRole && !hasRole) {
                guild.addRoleToMember(member, role).queue();
                addedRoles.add(role.getName());
            } else if (!shouldHaveRole && hasRole) {
                guild.removeRoleFromMember(member, role).queue();
                removedRoles.add(role.getName());
            }
        }
    }

    private void assignEnumRole(Guild guild, Member member, String guildId,
                                RoleCategory category, String currentValue,
                                List<String> addedRoles, List<String> removedRoles) {
        List<EnumRoleMapping> allMappings = roleMappingService.getEnumMappings(guildId, category);
        if (allMappings.isEmpty()) {
            return;
        }

        Set<String> matchingRoleIds = allMappings.stream()
                .filter(m -> m.getEnumValue().equals(currentValue))
                .map(EnumRoleMapping::getRoleId)
                .collect(Collectors.toSet());

        for (var mapping : allMappings) {
            Role role = guild.getRoleById(mapping.getRoleId());
            if (role == null) {
                continue;
            }

            boolean hasRole = member.getRoles().contains(role);
            boolean shouldHaveRole = matchingRoleIds.contains(mapping.getRoleId());

            if (shouldHaveRole && !hasRole) {
                guild.addRoleToMember(member, role).queue();
                addedRoles.add(role.getName());
            } else if (!shouldHaveRole && hasRole) {
                guild.removeRoleFromMember(member, role).queue();
                removedRoles.add(role.getName());
            }
        }
    }
}
