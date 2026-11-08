package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.discordbot.entity.CommunityAlertType;
import com.outlasttrialsstats.discordbot.entity.CommunityEventAlertRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityEventAlertRuleRepository extends JpaRepository<CommunityEventAlertRule, Long> {

    List<CommunityEventAlertRule> findByGuildId(String guildId);

    Optional<CommunityEventAlertRule> findByGuildIdAndAlertType(String guildId, CommunityAlertType alertType);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CommunityEventAlertRule r SET r.roleId = null WHERE r.guildId = :guildId AND r.roleId = :roleId")
    int clearRole(@Param("guildId") String guildId, @Param("roleId") String roleId);
}
