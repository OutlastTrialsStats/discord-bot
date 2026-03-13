package com.outlasttrialsstats.discordbot.feature.setup.service;

import com.outlasttrialsstats.backend.api.model.ActiveReagentSkillType;
import com.outlasttrialsstats.discordbot.entity.PrestigeRoleMapping;
import com.outlasttrialsstats.discordbot.entity.ReagentSkillRoleMapping;
import com.outlasttrialsstats.discordbot.repository.PrestigeRoleMappingRepository;
import com.outlasttrialsstats.discordbot.repository.ReagentSkillRoleMappingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleMappingService {

    private final PrestigeRoleMappingRepository prestigeRoleRepo;
    private final ReagentSkillRoleMappingRepository skillRoleRepo;

    @Transactional
    public void savePrestigeMapping(String guildId, int threshold, String roleId) {
        var existing = prestigeRoleRepo.findByGuildIdAndMinPrestige(guildId, threshold);
        if (existing.isPresent()) {
            existing.get().setRoleId(roleId);
        } else {
            prestigeRoleRepo.save(new PrestigeRoleMapping(guildId, threshold, roleId));
        }
    }

    @Transactional
    public void saveSkillMapping(String guildId, ActiveReagentSkillType skill, String roleId) {
        var existing = skillRoleRepo.findByGuildIdAndSkill(guildId, skill);
        if (existing.isPresent()) {
            existing.get().setRoleId(roleId);
        } else {
            skillRoleRepo.save(new ReagentSkillRoleMapping(guildId, skill, roleId));
        }
    }

    @Transactional
    public void removePrestigeMapping(String guildId, int threshold) {
        prestigeRoleRepo.deleteByGuildIdAndMinPrestige(guildId, threshold);
    }

    @Transactional
    public void removeSkillMapping(String guildId, ActiveReagentSkillType skill) {
        skillRoleRepo.deleteByGuildIdAndSkill(guildId, skill);
    }

    public List<String> getAllRoleIds(String guildId) {
        return java.util.stream.Stream.concat(
                prestigeRoleRepo.findByGuildId(guildId).stream().map(PrestigeRoleMapping::getRoleId),
                skillRoleRepo.findByGuildId(guildId).stream().map(ReagentSkillRoleMapping::getRoleId)
        ).toList();
    }

    @Transactional
    public void deleteAllMappings(String guildId) {
        prestigeRoleRepo.deleteAll(prestigeRoleRepo.findByGuildId(guildId));
        skillRoleRepo.deleteAll(skillRoleRepo.findByGuildId(guildId));
    }
}
