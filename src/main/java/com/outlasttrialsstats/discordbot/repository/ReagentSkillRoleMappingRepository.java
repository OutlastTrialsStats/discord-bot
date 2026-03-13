package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.backend.api.model.ActiveReagentSkillType;
import com.outlasttrialsstats.discordbot.entity.ReagentSkillRoleMapping;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReagentSkillRoleMappingRepository extends JpaRepository<ReagentSkillRoleMapping, Long> {

    List<ReagentSkillRoleMapping> findByGuildId(String guildId);

    Optional<ReagentSkillRoleMapping> findByGuildIdAndSkill(String guildId, ActiveReagentSkillType skill);

    void deleteByGuildIdAndSkill(String guildId, ActiveReagentSkillType skill);
}
