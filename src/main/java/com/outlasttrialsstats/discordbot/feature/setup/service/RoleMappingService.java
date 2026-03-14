package com.outlasttrialsstats.discordbot.feature.setup.service;

import com.outlasttrialsstats.discordbot.entity.EnumRoleMapping;
import com.outlasttrialsstats.discordbot.entity.RankedRoleMapping;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.repository.EnumRoleMappingRepository;
import com.outlasttrialsstats.discordbot.repository.RankedRoleMappingRepository;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleMappingService {

    private final EnumRoleMappingRepository enumRoleRepo;
    private final RankedRoleMappingRepository rankedRoleRepo;

    // --- Enum-based mappings (Skill, Platform, AccountType) ---

    @Transactional
    public void saveEnumMapping(String guildId, RoleCategory category, String enumValue, String roleId) {
        var existing = enumRoleRepo.findByGuildIdAndCategoryAndEnumValue(guildId, category, enumValue);
        if (existing.isPresent()) {
            existing.get().setRoleId(roleId);
        } else {
            enumRoleRepo.save(new EnumRoleMapping(guildId, category, enumValue, roleId));
        }
    }

    @Transactional
    public void removeEnumMapping(String guildId, RoleCategory category, String enumValue) {
        enumRoleRepo.deleteByGuildIdAndCategoryAndEnumValue(guildId, category, enumValue);
    }

    public List<EnumRoleMapping> getEnumMappings(String guildId, RoleCategory category) {
        return enumRoleRepo.findByGuildIdAndCategory(guildId, category);
    }

    // --- Ranked mappings (Prestige, InvasionRanking) ---

    @Transactional
    public void saveRankedMapping(String guildId, RoleCategory category, int minRank, String roleId) {
        var existing = rankedRoleRepo.findByGuildIdAndCategoryAndMinRank(guildId, category, minRank);
        if (existing.isPresent()) {
            existing.get().setRoleId(roleId);
        } else {
            rankedRoleRepo.save(new RankedRoleMapping(guildId, category, minRank, roleId));
        }
    }

    @Transactional
    public void removeRankedMapping(String guildId, RoleCategory category, int minRank) {
        rankedRoleRepo.deleteByGuildIdAndCategoryAndMinRank(guildId, category, minRank);
    }

    public List<RankedRoleMapping> getRankedMappings(String guildId, RoleCategory category) {
        return rankedRoleRepo.findByGuildIdAndCategory(guildId, category);
    }

    public java.util.Optional<RankedRoleMapping> getBestRankedMapping(String guildId, RoleCategory category, int rank) {
        return rankedRoleRepo.findFirstByGuildIdAndCategoryAndMinRankLessThanEqualOrderByMinRankDesc(
                guildId, category, rank);
    }

    // --- Common operations ---

    public List<String> getAllRoleIds(String guildId) {
        return Stream.concat(
                enumRoleRepo.findByGuildId(guildId).stream().map(EnumRoleMapping::getRoleId),
                rankedRoleRepo.findByGuildId(guildId).stream().map(RankedRoleMapping::getRoleId)
        ).toList();
    }

    @Transactional
    public void deleteAllMappings(String guildId) {
        enumRoleRepo.deleteByGuildId(guildId);
        rankedRoleRepo.deleteByGuildId(guildId);
    }
}
