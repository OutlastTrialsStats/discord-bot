package com.outlasttrialsstats.discordbot.feature.setup.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.outlasttrialsstats.discordbot.IntegrationTest;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.repository.EnumRoleMappingRepository;
import com.outlasttrialsstats.discordbot.repository.RankedRoleMappingRepository;
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
    void saveEnumMapping_newMapping_persists() {
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-1");

        var mappings = roleMappingService.getEnumMappings(GUILD_ID, RoleCategory.REAGENT_RIG);
        assertThat(mappings).hasSize(1);
        assertThat(mappings.getFirst().getRoleId()).isEqualTo("role-1");
    }

    @Test
    void saveEnumMapping_existingMapping_updatesRoleId() {
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-1");
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-2");

        var mappings = roleMappingService.getEnumMappings(GUILD_ID, RoleCategory.REAGENT_RIG);
        assertThat(mappings).hasSize(1);
        assertThat(mappings.getFirst().getRoleId()).isEqualTo("role-2");
    }

    @Test
    void removeEnumMapping_deletesCorrectEntry() {
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-1");
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "HEAL", "role-2");

        roleMappingService.removeEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN");

        var mappings = roleMappingService.getEnumMappings(GUILD_ID, RoleCategory.REAGENT_RIG);
        assertThat(mappings).hasSize(1);
        assertThat(mappings.getFirst().getEnumValue()).isEqualTo("HEAL");
    }

    @Test
    void saveRankedMapping_newMapping_persists() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-10");

        var mappings = roleMappingService.getRankedMappings(GUILD_ID, RoleCategory.PRESTIGE);
        assertThat(mappings).hasSize(1);
        assertThat(mappings.getFirst().getRoleId()).isEqualTo("role-10");
    }

    @Test
    void saveRankedMapping_existingMapping_updatesRoleId() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-old");
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-new");

        var mappings = roleMappingService.getRankedMappings(GUILD_ID, RoleCategory.PRESTIGE);
        assertThat(mappings).hasSize(1);
        assertThat(mappings.getFirst().getRoleId()).isEqualTo("role-new");
    }

    @Test
    void removeRankedMapping_deletesCorrectEntry() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-10");
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 20, "role-20");

        roleMappingService.removeRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10);

        var mappings = roleMappingService.getRankedMappings(GUILD_ID, RoleCategory.PRESTIGE);
        assertThat(mappings).hasSize(1);
        assertThat(mappings.getFirst().getMinRank()).isEqualTo(20);
    }

    @Test
    void getBestRankedMapping_returnsHighestQualifyingThreshold() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 1, "role-1");
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-10");
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 20, "role-20");

        var result = roleMappingService.getBestRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 15);
        assertThat(result).isPresent();
        assertThat(result.get().getMinRank()).isEqualTo(10);
    }

    @Test
    void getBestRankedMapping_withExactMatch_returnsExact() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-10");

        var result = roleMappingService.getBestRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10);
        assertThat(result).isPresent();
        assertThat(result.get().getMinRank()).isEqualTo(10);
    }

    @Test
    void getBestRankedMapping_belowAll_returnsEmpty() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "role-10");

        var result = roleMappingService.getBestRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 5);
        assertThat(result).isEmpty();
    }

    @Test
    void getAllRoleIds_combinesBothTypes() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 1, "ranked-role");
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "enum-role");

        var roleIds = roleMappingService.getAllRoleIds(GUILD_ID);
        assertThat(roleIds).containsExactlyInAnyOrder("ranked-role", "enum-role");
    }

    @Test
    void getAllRoleIds_noMappings_returnsEmptyList() {
        var roleIds = roleMappingService.getAllRoleIds(GUILD_ID);
        assertThat(roleIds).isEmpty();
    }

    @Test
    void deleteAllMappings_clearsEverythingForGuild() {
        roleMappingService.saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 1, "role-1");
        roleMappingService.saveEnumMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-2");
        roleMappingService.saveRankedMapping("guild-2", RoleCategory.PRESTIGE, 1, "role-3");

        roleMappingService.deleteAllMappings(GUILD_ID);

        assertThat(roleMappingService.getAllRoleIds(GUILD_ID)).isEmpty();
        assertThat(roleMappingService.getAllRoleIds("guild-2")).hasSize(1);
    }
}
