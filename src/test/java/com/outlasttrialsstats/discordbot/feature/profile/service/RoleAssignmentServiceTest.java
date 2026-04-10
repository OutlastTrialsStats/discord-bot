package com.outlasttrialsstats.discordbot.feature.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.ActiveReagentSkillType;
import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import com.outlasttrialsstats.discordbot.entity.EnumRoleMapping;
import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.entity.RankedRoleMapping;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentServiceTest {

    @Mock
    private TOTStatsApiClient statsApiClient;

    @Mock
    private RoleMappingService roleMappingService;

    @Mock
    private GuildServerRepository guildServerRepository;

    @InjectMocks
    private RoleAssignmentService roleAssignmentService;

    @Mock
    private Guild guild;

    @Mock
    private Member member;

    private static final String GUILD_ID = "guild-1";
    private static final String MEMBER_ID = "member-1";

    @BeforeEach
    void setUp() {
        when(guild.getId()).thenReturn(GUILD_ID);
        when(member.getId()).thenReturn(MEMBER_ID);
    }

    @Test
    void assignRoles_memberNotVerified_returnsNotVerified() {
        when(statsApiClient.getProfile(MEMBER_ID)).thenReturn(Optional.empty());

        var result = roleAssignmentService.assignRoles(guild, member);

        assertThat(result.verified()).isFalse();
        verify(roleMappingService, never()).getRankedMappings(any(), any());
    }

    @Test
    void assignRoles_noMappingsConfigured_returnsEmptyChanges() {
        stubProfileAndEmptyMappings(createProfile(0, 0, 0, null));

        var result = roleAssignmentService.assignRoles(guild, member);

        assertThat(result.verified()).isTrue();
        assertThat(result.hasChanges()).isFalse();
    }

    @Test
    void assignRoles_prestigeMapping_addsCorrectRole() {
        stubProfileAndEmptyMappings(createProfile(25, 0, 0, null));

        var mapping20 = rankedMapping(20, "role-20");
        var mapping30 = rankedMapping(30, "role-30");
        when(roleMappingService.getRankedMappings(GUILD_ID, RoleCategory.PRESTIGE))
                .thenReturn(List.of(mapping20, mapping30));
        when(roleMappingService.getBestRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 25))
                .thenReturn(Optional.of(mapping20));

        Role role20 = mockRole("Prestige 20+");
        Role role30 = mock(Role.class);
        when(guild.getRoleById("role-20")).thenReturn(role20);
        when(guild.getRoleById("role-30")).thenReturn(role30);
        when(member.getRoles()).thenReturn(List.of());
        stubAddRole(member, role20);

        var result = roleAssignmentService.assignRoles(guild, member);

        assertThat(result.verified()).isTrue();
        assertThat(result.addedRoles()).containsExactly("Prestige 20+");
        verify(guild).addRoleToMember(member, role20);
        verify(guild, never()).addRoleToMember(member, role30);
    }

    @Test
    void assignRoles_memberAlreadyHasCorrectRole_noChanges() {
        stubProfileAndEmptyMappings(createProfile(25, 0, 0, null));

        var mapping20 = rankedMapping(20, "role-20");
        when(roleMappingService.getRankedMappings(GUILD_ID, RoleCategory.PRESTIGE))
                .thenReturn(List.of(mapping20));
        when(roleMappingService.getBestRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 25))
                .thenReturn(Optional.of(mapping20));

        // Plain mock — getName() is never called since no role changes occur
        Role role20 = mock(Role.class);
        when(guild.getRoleById("role-20")).thenReturn(role20);
        when(member.getRoles()).thenReturn(List.of(role20));

        var result = roleAssignmentService.assignRoles(guild, member);

        assertThat(result.addedRoles()).isEmpty();
        assertThat(result.removedRoles()).isEmpty();
        verify(guild, never()).addRoleToMember(any(), any());
        verify(guild, never()).removeRoleFromMember(any(), any());
    }

    @Test
    void assignRoles_memberHasWrongRole_removesOldAndAddsNew() {
        stubProfileAndEmptyMappings(createProfile(25, 0, 0, null));

        var mapping10 = rankedMapping(10, "role-10");
        var mapping20 = rankedMapping(20, "role-20");
        when(roleMappingService.getRankedMappings(GUILD_ID, RoleCategory.PRESTIGE))
                .thenReturn(List.of(mapping10, mapping20));
        when(roleMappingService.getBestRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 25))
                .thenReturn(Optional.of(mapping20));

        Role role10 = mockRole("Prestige 10+");
        Role role20 = mockRole("Prestige 20+");
        when(guild.getRoleById("role-10")).thenReturn(role10);
        when(guild.getRoleById("role-20")).thenReturn(role20);
        when(member.getRoles()).thenReturn(List.of(role10));
        stubAddRole(member, role20);
        stubRemoveRole(member, role10);

        var result = roleAssignmentService.assignRoles(guild, member);

        assertThat(result.addedRoles()).containsExactly("Prestige 20+");
        assertThat(result.removedRoles()).containsExactly("Prestige 10+");
        verify(guild).addRoleToMember(member, role20);
        verify(guild).removeRoleFromMember(member, role10);
    }

    @Test
    void assignRoles_enumMapping_addsSkillRole() {
        stubProfileAndEmptyMappings(createProfile(0, 0, 0, ActiveReagentSkillType.STUN));

        var stunMapping = enumMapping();
        when(roleMappingService.getEnumMappings(GUILD_ID, RoleCategory.REAGENT_RIG))
                .thenReturn(List.of(stunMapping));

        Role stunRole = mockRole("Stun");
        when(guild.getRoleById("role-stun")).thenReturn(stunRole);
        when(member.getRoles()).thenReturn(List.of());
        stubAddRole(member, stunRole);

        var result = roleAssignmentService.assignRoles(guild, member);

        assertThat(result.addedRoles()).containsExactly("Stun");
        verify(guild).addRoleToMember(member, stunRole);
    }

    @Test
    void assignRoles_roleNotFoundInGuild_skipsGracefully() {
        stubProfileAndEmptyMappings(createProfile(25, 0, 0, null));

        var mapping = rankedMapping(20, "deleted-role");
        when(roleMappingService.getRankedMappings(GUILD_ID, RoleCategory.PRESTIGE))
                .thenReturn(List.of(mapping));
        when(roleMappingService.getBestRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 25))
                .thenReturn(Optional.of(mapping));

        when(guild.getRoleById("deleted-role")).thenReturn(null);

        var result = roleAssignmentService.assignRoles(guild, member);

        assertThat(result.verified()).isTrue();
        assertThat(result.hasChanges()).isFalse();
    }

    @Test
    void assignRoles_nullProfileFields_handledGracefully() {
        stubProfileAndEmptyMappings(createProfile(null, null, null, null));

        var result = roleAssignmentService.assignRoles(guild, member);

        assertThat(result.verified()).isTrue();
        assertThat(result.hasChanges()).isFalse();
    }

    @Test
    void assignRoles_autoNicknameEnabled_updatesNickname() {
        var profile = createProfile(0, 0, 0, null);
        profile.setDisplayName("GamePlayer123");
        stubProfileAndEmptyMappings(profile);

        var server = new GuildServer(GUILD_ID);
        server.setAutoNickname(true);
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.of(server));
        when(member.getEffectiveName()).thenReturn("OldNickname");

        @SuppressWarnings("unchecked")
        AuditableRestAction<Void> modifyAction = mock(AuditableRestAction.class);
        when(guild.modifyNickname(member, "GamePlayer123")).thenReturn(modifyAction);

        roleAssignmentService.assignRoles(guild, member);

        verify(guild).modifyNickname(member, "GamePlayer123");
    }

    @Test
    void assignRoles_autoNicknameDisabled_doesNotUpdateNickname() {
        var profile = createProfile(0, 0, 0, null);
        profile.setDisplayName("GamePlayer123");
        stubProfileAndEmptyMappings(profile);

        var server = new GuildServer(GUILD_ID);
        server.setAutoNickname(false);
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.of(server));

        roleAssignmentService.assignRoles(guild, member);

        verify(guild, never()).modifyNickname(any(), any());
    }

    @Test
    void assignRoles_nicknameAlreadyCorrect_doesNotModify() {
        var profile = createProfile(0, 0, 0, null);
        profile.setDisplayName("GamePlayer123");
        stubProfileAndEmptyMappings(profile);

        var server = new GuildServer(GUILD_ID);
        server.setAutoNickname(true);
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.of(server));
        when(member.getEffectiveName()).thenReturn("GamePlayer123");

        roleAssignmentService.assignRoles(guild, member);

        verify(guild, never()).modifyNickname(any(), any());
    }

    @Test
    void assignRoles_noGuildServerEntry_doesNotUpdateNickname() {
        var profile = createProfile(0, 0, 0, null);
        profile.setDisplayName("GamePlayer123");
        stubProfileAndEmptyMappings(profile);

        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.empty());

        roleAssignmentService.assignRoles(guild, member);

        verify(guild, never()).modifyNickname(any(), any());
    }

    private void stubProfileAndEmptyMappings(DiscordProfileResponse profile) {
        when(statsApiClient.getProfile(MEMBER_ID)).thenReturn(Optional.of(profile));
        when(roleMappingService.getRankedMappings(eq(GUILD_ID), any())).thenReturn(List.of());
        when(roleMappingService.getEnumMappings(eq(GUILD_ID), any())).thenReturn(List.of());
    }

    private void stubAddRole(Member member, Role role) {
        when(guild.addRoleToMember(member, role)).thenReturn(mock());
    }

    private void stubRemoveRole(Member member, Role role) {
        when(guild.removeRoleFromMember(member, role)).thenReturn(mock());
    }

    private DiscordProfileResponse createProfile(Integer prestige, Integer level,
                                                 Integer totalInvasionMatches, ActiveReagentSkillType skill) {
        var profile = new DiscordProfileResponse();
        profile.setPrestigeLevel(prestige);
        profile.setLevel(level);
        profile.setInvasionRanking(null);
        profile.setTotalInvasionMatchesPlayed(totalInvasionMatches);
        profile.setActiveReagentSkill(skill);
        profile.setPlatformType(null);
        profile.setAccountCreationType(null);
        return profile;
    }

    private RankedRoleMapping rankedMapping(int minRank, String roleId) {
        return new RankedRoleMapping(GUILD_ID, RoleCategory.PRESTIGE, minRank, roleId);
    }

    private EnumRoleMapping enumMapping() {
        return new EnumRoleMapping(GUILD_ID, RoleCategory.REAGENT_RIG, "STUN", "role-stun");
    }

    private Role mockRole(String name) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(name);
        return role;
    }
}
