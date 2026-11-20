package com.outlasttrialsstats.discordbot.feature.setup.service;

import com.outlasttrialsstats.discordbot.entity.EnumRoleMapping;
import com.outlasttrialsstats.discordbot.entity.RankedRoleMapping;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.repository.EnumRoleMappingRepository;
import com.outlasttrialsstats.discordbot.repository.RankedRoleMappingRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleMappingService {

    private final EnumRoleMappingRepository enumRoleRepo;
    private final RankedRoleMappingRepository rankedRoleRepo;


    @Transactional
    public void saveEnumMapping(String guildId, RoleCategory category, String enumValue, String roleId,
                                boolean botCreated) {
        var existing = enumRoleRepo.findByGuildIdAndCategoryAndEnumValue(guildId, category, enumValue);
        EnumRoleMapping mapping = existing.orElseGet(() -> new EnumRoleMapping(guildId, category, enumValue, roleId));
        mapping.setBotCreated(keepBotCreated(mapping.getRoleId(), mapping.isBotCreated(), roleId, botCreated));
        mapping.setRoleId(roleId);
        if (existing.isEmpty()) {
            enumRoleRepo.save(mapping);
        }
    }

    @Transactional
    public Optional<String> removeEnumMapping(String guildId, RoleCategory category, String enumValue) {
        Optional<EnumRoleMapping> existing = enumRoleRepo.findByGuildIdAndCategoryAndEnumValue(guildId, category, enumValue);
        existing.ifPresent(enumRoleRepo::delete);
        return existing.map(EnumRoleMapping::getRoleId);
    }

    public Optional<String> findEnumMappingRoleId(String guildId, RoleCategory category, String enumValue) {
        return enumRoleRepo.findByGuildIdAndCategoryAndEnumValue(guildId, category, enumValue)
                .map(EnumRoleMapping::getRoleId);
    }

    private static boolean keepBotCreated(String currentRoleId, boolean currentBotCreated,
                                          String newRoleId, boolean newBotCreated) {
        return newBotCreated || (currentBotCreated && newRoleId.equals(currentRoleId));
    }

    public List<EnumRoleMapping> getEnumMappings(String guildId, RoleCategory category) {
        return enumRoleRepo.findByGuildIdAndCategory(guildId, category);
    }


    @Transactional
    public void saveRankedMapping(String guildId, RoleCategory category, int minRank, String roleId,
                                  boolean botCreated) {
        var existing = rankedRoleRepo.findByGuildIdAndCategoryAndMinRank(guildId, category, minRank);
        RankedRoleMapping mapping = existing.orElseGet(() -> new RankedRoleMapping(guildId, category, minRank, roleId));
        mapping.setBotCreated(keepBotCreated(mapping.getRoleId(), mapping.isBotCreated(), roleId, botCreated));
        mapping.setRoleId(roleId);
        if (existing.isEmpty()) {
            rankedRoleRepo.save(mapping);
        }
    }

    @Transactional
    public Optional<String> removeRankedMapping(String guildId, RoleCategory category, int minRank) {
        Optional<RankedRoleMapping> existing = rankedRoleRepo.findByGuildIdAndCategoryAndMinRank(guildId, category, minRank);
        existing.ifPresent(rankedRoleRepo::delete);
        return existing.map(RankedRoleMapping::getRoleId);
    }

    public Optional<String> findRankedMappingRoleId(String guildId, RoleCategory category, int minRank) {
        return rankedRoleRepo.findByGuildIdAndCategoryAndMinRank(guildId, category, minRank)
                .map(RankedRoleMapping::getRoleId);
    }

    public List<RankedRoleMapping> getRankedMappings(String guildId, RoleCategory category) {
        return rankedRoleRepo.findByGuildIdAndCategory(guildId, category);
    }

    public Optional<RankedRoleMapping> getBestRankedMapping(String guildId, RoleCategory category, int rank) {
        return rankedRoleRepo.findFirstByGuildIdAndCategoryAndMinRankLessThanEqualOrderByMinRankDesc(
                guildId, category, rank);
    }


    public List<MappedRole> getMappedRoles(String guildId) {
        Map<String, Boolean> merged = new LinkedHashMap<>();
        enumRoleRepo.findByGuildId(guildId)
                .forEach(m -> merged.merge(m.getRoleId(), m.isBotCreated(), Boolean::logicalOr));
        rankedRoleRepo.findByGuildId(guildId)
                .forEach(m -> merged.merge(m.getRoleId(), m.isBotCreated(), Boolean::logicalOr));
        return merged.entrySet().stream()
                .map(e -> new MappedRole(e.getKey(), e.getValue()))
                .toList();
    }

    public record MappedRole(String roleId, boolean botCreated) {
    }

    public Optional<RoleCategory> findOtherCategoryUsingRole(String guildId, String roleId, RoleCategory category) {
        return Stream.concat(
                enumRoleRepo.findByGuildIdAndRoleIdAndCategoryNot(guildId, roleId, category).stream()
                        .map(EnumRoleMapping::getCategory),
                rankedRoleRepo.findByGuildIdAndRoleIdAndCategoryNot(guildId, roleId, category).stream()
                        .map(RankedRoleMapping::getCategory)
        ).findFirst();
    }

    @Transactional
    public void deleteMappingsByRoleIds(String guildId, Collection<String> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        enumRoleRepo.deleteByGuildIdAndRoleIdIn(guildId, roleIds);
        rankedRoleRepo.deleteByGuildIdAndRoleIdIn(guildId, roleIds);
    }

    @Transactional
    public void deleteMappingsByRoleId(String guildId, String roleId) {
        enumRoleRepo.deleteByGuildIdAndRoleId(guildId, roleId);
        rankedRoleRepo.deleteByGuildIdAndRoleId(guildId, roleId);
    }
}
