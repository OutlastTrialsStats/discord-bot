package com.outlasttrialsstats.discordbot.feature.setup.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.outlasttrialsstats.discordbot.IntegrationTest;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.repository.EnumRoleMappingRepository;
import com.outlasttrialsstats.discordbot.repository.RankedRoleMappingRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@IntegrationTest
@Import(RoleMappingService.class)
class RoleMappingServiceTestIT {

    @Autowired
    private RoleMappingService roleMappingService;

    @Autowired
    private EnumRoleMappingRepository enumRoleRepo;

    @Autowired
    private RankedRoleMappingRepository rankedRoleRepo;

    private static final String GUILD_ID = "guild-1";

    @BeforeEach
    void setUp() {
        enumRoleRepo.deleteAll();
        rankedRoleRepo.deleteAll();
    }

    @Test
    void countByCategory_countsBothMappingTypesOfThatGuild() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.LEVEL, 10, "role-1", false);
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.LEVEL, 20, "role-2", false);
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.PLATFORM, "STEAM", "role-3", false);
        roleMappingService.saveRankedMapping("guild-2", RoleCategory.LEVEL, 30, "role-4", false);

        assertThat(roleMappingService.countByCategory(GUILD_ID))
                .containsOnly(java.util.Map.entry(RoleCategory.LEVEL, 2), java.util.Map.entry(RoleCategory.PLATFORM, 1));
    }

    @Test
    void findOtherCategoryUsingRole_roleUsedInOtherCategory_returnsThatCategory() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.LEVEL, 10, "role-1", false);
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.PLATFORM, "STEAM", "role-2", false);

        assertThat(roleMappingService.findOtherCategoryUsingRole(GUILD_ID, "role-1", RoleCategory.PRESTIGE))
                .contains(RoleCategory.LEVEL);
        assertThat(roleMappingService.findOtherCategoryUsingRole(GUILD_ID, "role-2", RoleCategory.PRESTIGE))
                .contains(RoleCategory.PLATFORM);
    }

    @Test
    void findOtherCategoryUsingRole_sameCategoryOrOtherGuild_returnsEmpty() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-1", false);
        roleMappingService.saveRankedMapping("guild-2", RoleCategory.LEVEL, 10, "role-1", false);

        assertThat(roleMappingService.findOtherCategoryUsingRole(GUILD_ID, "role-1", RoleCategory.PRESTIGE))
                .isEmpty();
    }


    @Test
    void saveMapping_defaultsToNotBotCreated() {
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.PLATFORM, "STEAM", "role-1", false);
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.LEVEL, 10, "role-2", false);

        assertThat(roleMappingService.getMappedRoles(GUILD_ID)).containsExactly(
                new RoleMappingService.MappedRole("role-1", false),
                new RoleMappingService.MappedRole("role-2", false));
    }

    @Test
    void saveMapping_botCreated_isReportedByGetMappedRoles() {
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.PLATFORM, "STEAM", "role-1", true);
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.LEVEL, 10, "role-2", true);

        assertThat(roleMappingService.getMappedRoles(GUILD_ID)).containsExactly(
                new RoleMappingService.MappedRole("role-1", true),
                new RoleMappingService.MappedRole("role-2", true));
    }

    @Test
    void saveMapping_relinkToExistingRole_clearsBotCreated() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.LEVEL, 10, "created-role", true);
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.LEVEL, 10, "linked-role", false);

        assertThat(roleMappingService.getMappedRoles(GUILD_ID))
                .containsExactly(new RoleMappingService.MappedRole("linked-role", false));
    }

    @Test
    void saveMapping_sameBotCreatedRoleSavedAgainAsLinked_keepsBotCreated() {
        // e.g. /setup basic run a second time matches the bot's own role by name
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.LEVEL, 10, "role-1", true);
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.LEVEL, 10, "role-1", false);
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.PLATFORM, "STEAM", "role-2", true);
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.PLATFORM, "STEAM", "role-2", false);

        assertThat(roleMappingService.getMappedRoles(GUILD_ID)).containsExactlyInAnyOrder(
                new RoleMappingService.MappedRole("role-1", true),
                new RoleMappingService.MappedRole("role-2", true));
    }

    @Test
    void getMappedRoles_sameRoleInSeveralMappings_returnsItOnceAndBotCreatedIfAnyIs() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.LEVEL, 10, "role-1", true);
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.LEVEL, 20, "role-1", false);
        roleMappingService.saveRankedMapping("guild-2", RoleCategory.LEVEL, 10, "role-9", true);

        assertThat(roleMappingService.getMappedRoles(GUILD_ID))
                .containsExactly(new RoleMappingService.MappedRole("role-1", true));
    }

    @Test
    void saveEnumMapping_newMapping_persists() {
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-1", false);

        var mappings = roleMappingService.getEnumMappings(GUILD_ID, RoleCategory.REAGENT_RIG);
        assertThat(mappings).hasSize(1);
        assertThat(mappings.getFirst().getRoleId()).isEqualTo("role-1");
    }

    @Test
    void saveEnumMapping_existingMapping_updatesRoleId() {
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-1", false);
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-2", false);

        var mappings = roleMappingService.getEnumMappings(GUILD_ID, RoleCategory.REAGENT_RIG);
        assertThat(mappings).hasSize(1);
        assertThat(mappings.getFirst().getRoleId()).isEqualTo("role-2");
    }

    @Test
    void removeEnumMapping_deletesCorrectEntry() {
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-1", false);
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "HEAL", "role-2", false);

        roleMappingService.removeEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN");

        var mappings = roleMappingService.getEnumMappings(GUILD_ID, RoleCategory.REAGENT_RIG);
        assertThat(mappings).hasSize(1);
        assertThat(mappings.getFirst().getEnumValue()).isEqualTo("HEAL");
    }

    @Test
    void saveRankedMapping_newMapping_persists() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-10", false);

        var mappings = roleMappingService.getRankedMappings(GUILD_ID, RoleCategory.PRESTIGE);
        assertThat(mappings).hasSize(1);
        assertThat(mappings.getFirst().getRoleId()).isEqualTo("role-10");
    }

    @Test
    void saveRankedMapping_existingMapping_updatesRoleId() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-old", false);
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-new", false);

        var mappings = roleMappingService.getRankedMappings(GUILD_ID, RoleCategory.PRESTIGE);
        assertThat(mappings).hasSize(1);
        assertThat(mappings.getFirst().getRoleId()).isEqualTo("role-new");
    }

    @Test
    void removeRankedMapping_deletesCorrectEntry() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-10", false);
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 20, "role-20", false);

        roleMappingService.removeRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10);

        var mappings = roleMappingService.getRankedMappings(GUILD_ID, RoleCategory.PRESTIGE);
        assertThat(mappings).hasSize(1);
        assertThat(mappings.getFirst().getMinRank()).isEqualTo(20);
    }

    @Test
    void getBestRankedMapping_returnsHighestQualifyingThreshold() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 1, "role-1", false);
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-10", false);
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 20, "role-20", false);

        var result = roleMappingService.getBestRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 15);
        assertThat(result).isPresent();
        assertThat(result.get().getMinRank()).isEqualTo(10);
    }

    @Test
    void getBestRankedMapping_withExactMatch_returnsExact() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-10", false);

        var result = roleMappingService.getBestRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10);
        assertThat(result).isPresent();
        assertThat(result.get().getMinRank()).isEqualTo(10);
    }

    @Test
    void getBestRankedMapping_belowAll_returnsEmpty() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-10", false);

        var result = roleMappingService.getBestRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 5);
        assertThat(result).isEmpty();
    }

    @Test
    void getMappedRoles_combinesBothTypes() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 1, "ranked-role", false);
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "enum-role", false);

        assertThat(roleMappingService.getMappedRoles(GUILD_ID))
                .extracting(RoleMappingService.MappedRole::roleId)
                .containsExactlyInAnyOrder("ranked-role", "enum-role");
    }

    @Test
    void getMappedRoles_noMappings_returnsEmptyList() {
        assertThat(roleMappingService.getMappedRoles(GUILD_ID)).isEmpty();
    }

    @Test
    void deleteMappingsByRoleIds_removesOnlyGivenRolesOfThatGuild() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 1, "role-1", false);
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-2", false);
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.PLATFORM, "STEAM", "role-3", false);
        roleMappingService.saveRankedMapping("guild-2", RoleCategory.PRESTIGE, 1, "role-1", false);

        roleMappingService.deleteMappingsByRoleIds(GUILD_ID, List.of("role-1", "role-2"));

        assertThat(roleMappingService.getMappedRoles(GUILD_ID))
                .extracting(RoleMappingService.MappedRole::roleId)
                .containsExactly("role-3");
        assertThat(roleMappingService.getMappedRoles("guild-2")).hasSize(1);
    }

    @Test
    void deleteMappingsByRoleIds_emptyCollection_deletesNothing() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 1, "role-1", false);

        roleMappingService.deleteMappingsByRoleIds(GUILD_ID, List.of());

        assertThat(roleMappingService.getMappedRoles(GUILD_ID)).hasSize(1);
    }
}
