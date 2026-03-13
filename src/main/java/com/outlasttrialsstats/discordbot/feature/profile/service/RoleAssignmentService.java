package com.outlasttrialsstats.discordbot.feature.profile.service;

import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import com.outlasttrialsstats.discordbot.entity.ReagentSkillRoleMapping;
import com.outlasttrialsstats.discordbot.feature.profile.dto.RoleAssignmentResult;
import com.outlasttrialsstats.discordbot.repository.PrestigeRoleMappingRepository;
import com.outlasttrialsstats.discordbot.repository.ReagentSkillRoleMappingRepository;
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
    private final PrestigeRoleMappingRepository prestigeRoleRepo;
    private final ReagentSkillRoleMappingRepository skillRoleRepo;

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

        log.debug("Member {} has prestige {} and active skill {}",
                member.getId(), profile.getPrestigeLevel(), profile.getActiveReagentSkill());

        assignPrestigeRoles(guild, member, profile, guildId, addedRoles, removedRoles);
        assignReagentSkillRoles(guild, member, profile, guildId, addedRoles, removedRoles);

        if (!addedRoles.isEmpty() || !removedRoles.isEmpty()) {
            log.info("Member {} in guild {}: added [{}], removed [{}]",
                    member.getId(), guildId, String.join(", ", addedRoles), String.join(", ", removedRoles));
        }

        return RoleAssignmentResult.of(addedRoles, removedRoles);
    }

    private void assignPrestigeRoles(Guild guild, Member member, DiscordProfileResponse profile,
                                     String guildId, List<String> addedRoles, List<String> removedRoles) {
        var allMappings = prestigeRoleRepo.findByGuildId(guildId);
        if (allMappings.isEmpty()) {
            log.debug("No prestige role mappings configured for guild {}", guildId);
            return;
        }

        var bestMapping = prestigeRoleRepo
                .findFirstByGuildIdAndMinPrestigeLessThanEqualOrderByMinPrestigeDesc(guildId, profile.getPrestigeLevel());

        String bestRoleId = bestMapping.map(m -> m.getRoleId()).orElse(null);
        log.debug("Best prestige mapping for member {} (prestige {}): {}",
                member.getId(), profile.getPrestigeLevel(),
                bestMapping.map(m -> "Prestige " + m.getMinPrestige() + "+").orElse("none"));

        for (var mapping : allMappings) {
            Role role = guild.getRoleById(mapping.getRoleId());
            if (role == null) {
                log.warn("Prestige role {} not found in guild {}", mapping.getRoleId(), guildId);
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

    private void assignReagentSkillRoles(Guild guild, Member member, DiscordProfileResponse profile,
                                         String guildId, List<String> addedRoles, List<String> removedRoles) {
        List<ReagentSkillRoleMapping> allSkillMappings = skillRoleRepo.findByGuildId(guildId);
        if (allSkillMappings.isEmpty()) {
            log.debug("No skill role mappings configured for guild {}", guildId);
            return;
        }

        Set<String> matchingRoleIds = allSkillMappings.stream()
                .filter(m -> m.getSkill() == profile.getActiveReagentSkill())
                .map(ReagentSkillRoleMapping::getRoleId)
                .collect(Collectors.toSet());

        log.debug("Active skill for member {}: {}, matching role IDs: {}",
                member.getId(), profile.getActiveReagentSkill(), matchingRoleIds);

        for (var mapping : allSkillMappings) {
            Role role = guild.getRoleById(mapping.getRoleId());
            if (role == null) {
                log.warn("Skill role {} not found in guild {}", mapping.getRoleId(), guildId);
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
