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
        var profileOpt = statsApiClient.getProfile(member.getId());
        if (profileOpt.isEmpty()) {
            return RoleAssignmentResult.notVerified();
        }

        DiscordProfileResponse profile = profileOpt.get();
        String guildId = guild.getId();
        List<String> addedRoles = new ArrayList<>();
        List<String> removedRoles = new ArrayList<>();

        assignPrestigeRoles(guild, member, profile, guildId, addedRoles);
        assignReagentSkillRoles(guild, member, profile, guildId, addedRoles, removedRoles);

        return RoleAssignmentResult.of(addedRoles, removedRoles);
    }

    private void assignPrestigeRoles(Guild guild, Member member, DiscordProfileResponse profile,
                                     String guildId, List<String> addedRoles) {
        var matchingMappings = prestigeRoleRepo
                .findByGuildIdAndMinPrestigeLessThanEqual(guildId, profile.getPrestigeLevel());

        for (var mapping : matchingMappings) {
            Role role = guild.getRoleById(mapping.getRoleId());
            if (role == null) {
                log.warn("Role {} not found in guild {}", mapping.getRoleId(), guildId);
                continue;
            }
            if (!member.getRoles().contains(role)) {
                guild.addRoleToMember(member, role).queue();
                addedRoles.add(role.getName());
            }
        }
    }

    private void assignReagentSkillRoles(Guild guild, Member member, DiscordProfileResponse profile,
                                         String guildId, List<String> addedRoles, List<String> removedRoles) {
        List<ReagentSkillRoleMapping> allSkillMappings = skillRoleRepo.findByGuildId(guildId);
        if (allSkillMappings.isEmpty()) {
            return;
        }

        Set<String> matchingRoleIds = allSkillMappings.stream()
                .filter(m -> m.getSkill() == profile.getActiveReagentSkill())
                .map(ReagentSkillRoleMapping::getRoleId)
                .collect(Collectors.toSet());

        for (var mapping : allSkillMappings) {
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
