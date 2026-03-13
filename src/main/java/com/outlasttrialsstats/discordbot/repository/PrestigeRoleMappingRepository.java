package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.discordbot.entity.PrestigeRoleMapping;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrestigeRoleMappingRepository extends JpaRepository<PrestigeRoleMapping, Long> {

    List<PrestigeRoleMapping> findByGuildId(String guildId);

    List<PrestigeRoleMapping> findByGuildIdAndMinPrestigeLessThanEqual(String guildId, int prestige);

    Optional<PrestigeRoleMapping> findByGuildIdAndMinPrestige(String guildId, int minPrestige);

    void deleteByGuildIdAndMinPrestige(String guildId, int minPrestige);
}
