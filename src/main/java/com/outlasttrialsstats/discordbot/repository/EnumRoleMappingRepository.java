package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.discordbot.entity.EnumRoleMapping;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnumRoleMappingRepository extends JpaRepository<EnumRoleMapping, Long> {

    List<EnumRoleMapping> findByGuildId(String guildId);

    List<EnumRoleMapping> findByGuildIdAndCategory(String guildId, RoleCategory category);

    Optional<EnumRoleMapping> findByGuildIdAndCategoryAndEnumValue(String guildId, RoleCategory category, String enumValue);

    void deleteByGuildIdAndCategoryAndEnumValue(String guildId, RoleCategory category, String enumValue);

    void deleteByGuildId(String guildId);
}
