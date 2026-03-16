package com.outlasttrialsstats.discordbot.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.outlasttrialsstats.discordbot.IntegrationTest;
import com.outlasttrialsstats.discordbot.entity.RankedRoleMapping;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class RankedRoleMappingRepositoryTestIT {

    @Autowired
    private RankedRoleMappingRepository repository;

    private static final String GUILD_ID = "guild-1";
    private static final String OTHER_GUILD_ID = "guild-2";

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findByGuildIdAndCategory_returnsMatchingMappings() {
        repository.save(new RankedRoleMapping(GUILD_ID, RoleCategory.PRESTIGE, 1, "role-1"));
        repository.save(new RankedRoleMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-2"));
        repository.save(new RankedRoleMapping(GUILD_ID, RoleCategory.LEVEL, 50, "role-3"));
        repository.save(new RankedRoleMapping(OTHER_GUILD_ID, RoleCategory.PRESTIGE, 1, "role-4"));

        var result = repository.findByGuildIdAndCategory(GUILD_ID, RoleCategory.PRESTIGE);

        assertThat(result)
                .hasSize(2)
                .extracting(RankedRoleMapping::getMinRank)
                .containsExactlyInAnyOrder(1, 10);
    }

    @Test
    void findBestRankedMapping_returnsBestMatch() {
        repository.save(new RankedRoleMapping(GUILD_ID, RoleCategory.PRESTIGE, 1, "role-1"));
        repository.save(new RankedRoleMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-10"));
        repository.save(new RankedRoleMapping(GUILD_ID, RoleCategory.PRESTIGE, 20, "role-20"));
        repository.save(new RankedRoleMapping(GUILD_ID, RoleCategory.PRESTIGE, 30, "role-30"));

        var result = repository.findFirstByGuildIdAndCategoryAndMinRankLessThanEqualOrderByMinRankDesc(
                GUILD_ID, RoleCategory.PRESTIGE, 25);
        assertThat(result).isPresent();
        assertThat(result.get().getMinRank()).isEqualTo(20);

        result = repository.findFirstByGuildIdAndCategoryAndMinRankLessThanEqualOrderByMinRankDesc(
                GUILD_ID, RoleCategory.PRESTIGE, 5);
        assertThat(result).isPresent();
        assertThat(result.get().getMinRank()).isEqualTo(1);

        result = repository.findFirstByGuildIdAndCategoryAndMinRankLessThanEqualOrderByMinRankDesc(
                GUILD_ID, RoleCategory.PRESTIGE, 30);
        assertThat(result).isPresent();
        assertThat(result.get().getMinRank()).isEqualTo(30);

        result = repository.findFirstByGuildIdAndCategoryAndMinRankLessThanEqualOrderByMinRankDesc(
                GUILD_ID, RoleCategory.PRESTIGE, 0);
        assertThat(result).isEmpty();
    }

    @Test
    void findByGuildIdAndCategoryAndMinRank_findsExact() {
        repository.save(new RankedRoleMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-10"));

        var result = repository.findByGuildIdAndCategoryAndMinRank(GUILD_ID, RoleCategory.PRESTIGE, 10);
        assertThat(result).isPresent();
        assertThat(result.get().getRoleId()).isEqualTo("role-10");
    }

    @Test
    void findByGuildIdAndCategoryAndMinRank_noMatch_returnsEmpty() {
        repository.save(new RankedRoleMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-10"));

        var result = repository.findByGuildIdAndCategoryAndMinRank(GUILD_ID, RoleCategory.PRESTIGE, 99);
        assertThat(result).isEmpty();
    }

    @Test
    void deleteByGuildIdAndCategoryAndMinRank_removesCorrectEntry() {
        repository.save(new RankedRoleMapping(GUILD_ID, RoleCategory.PRESTIGE, 1, "role-1"));
        repository.save(new RankedRoleMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-10"));

        repository.deleteByGuildIdAndCategoryAndMinRank(GUILD_ID, RoleCategory.PRESTIGE, 1);

        var remaining = repository.findByGuildIdAndCategory(GUILD_ID, RoleCategory.PRESTIGE);
        assertThat(remaining)
                .hasSize(1)
                .extracting(RankedRoleMapping::getMinRank)
                .containsExactly(10);
    }

    @Test
    void deleteByGuildId_removesAllForGuild() {
        repository.save(new RankedRoleMapping(GUILD_ID, RoleCategory.PRESTIGE, 1, "role-1"));
        repository.save(new RankedRoleMapping(GUILD_ID, RoleCategory.LEVEL, 50, "role-2"));
        repository.save(new RankedRoleMapping(OTHER_GUILD_ID, RoleCategory.PRESTIGE, 1, "role-3"));

        repository.deleteByGuildId(GUILD_ID);

        assertThat(repository.findByGuildId(GUILD_ID)).isEmpty();
        assertThat(repository.findByGuildId(OTHER_GUILD_ID)).hasSize(1);
    }
}
