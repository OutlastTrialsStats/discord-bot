package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.discordbot.entity.RankedRoleMapping;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankedRoleMappingRepository extends JpaRepository<RankedRoleMapping, Long> {

    List<RankedRoleMapping> findByGuildId(String guildId);

    List<RankedRoleMapping> findByGuildIdAndCategory(String guildId, RoleCategory category);

    Optional<RankedRoleMapping> findFirstByGuildIdAndCategoryAndMinRankLessThanEqualOrderByMinRankDesc(
            String guildId, RoleCategory category, int rank);

    Optional<RankedRoleMapping> findByGuildIdAndCategoryAndMinRank(String guildId, RoleCategory category, int minRank);

    void deleteByGuildIdAndCategoryAndMinRank(String guildId, RoleCategory category, int minRank);

    void deleteByGuildId(String guildId);
}
