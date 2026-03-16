package com.outlasttrialsstats.discordbot.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.outlasttrialsstats.discordbot.IntegrationTest;
import com.outlasttrialsstats.discordbot.entity.EnumRoleMapping;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class EnumRoleMappingRepositoryTestIT {

    @Autowired
    private EnumRoleMappingRepository repository;

    private static final String GUILD_ID = "guild-1";
    private static final String OTHER_GUILD_ID = "guild-2";

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findByGuildIdAndCategory_returnsMatchingMappings() {
        repository.save(new EnumRoleMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-1"));
        repository.save(new EnumRoleMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "HEAL", "role-2"));
        repository.save(new EnumRoleMapping(GUILD_ID, RoleCategory.PLATFORM, "STEAM", "role-3"));
        repository.save(new EnumRoleMapping(OTHER_GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-4"));

        var result = repository.findByGuildIdAndCategory(GUILD_ID, RoleCategory.REAGENT_RIG);

        assertThat(result)
                .hasSize(2)
                .extracting(EnumRoleMapping::getEnumValue)
                .containsExactlyInAnyOrder("STUN", "HEAL");
    }

    @Test
    void findByGuildIdAndCategoryAndEnumValue_findsExact() {
        repository.save(new EnumRoleMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-1"));

        var result = repository.findByGuildIdAndCategoryAndEnumValue(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN");
        assertThat(result).isPresent();
        assertThat(result.get().getRoleId()).isEqualTo("role-1");
    }

    @Test
    void findByGuildIdAndCategoryAndEnumValue_noMatch_returnsEmpty() {
        repository.save(new EnumRoleMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-1"));

        var result = repository.findByGuildIdAndCategoryAndEnumValue(GUILD_ID, RoleCategory.REAGENT_RIG, "XRAY");
        assertThat(result).isEmpty();
    }

    @Test
    void deleteByGuildIdAndCategoryAndEnumValue_removesCorrectEntry() {
        repository.save(new EnumRoleMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-1"));
        repository.save(new EnumRoleMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "HEAL", "role-2"));

        repository.deleteByGuildIdAndCategoryAndEnumValue(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN");

        var remaining = repository.findByGuildIdAndCategory(GUILD_ID, RoleCategory.REAGENT_RIG);
        assertThat(remaining)
                .hasSize(1)
                .extracting(EnumRoleMapping::getEnumValue)
                .containsExactly("HEAL");
    }

    @Test
    void deleteByGuildId_removesAllForGuild() {
        repository.save(new EnumRoleMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-1"));
        repository.save(new EnumRoleMapping(GUILD_ID, RoleCategory.PLATFORM, "STEAM", "role-2"));
        repository.save(new EnumRoleMapping(OTHER_GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-3"));

        repository.deleteByGuildId(GUILD_ID);

        assertThat(repository.findByGuildId(GUILD_ID)).isEmpty();
        assertThat(repository.findByGuildId(OTHER_GUILD_ID)).hasSize(1);
    }
}
