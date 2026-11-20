package com.outlasttrialsstats.discordbot.feature.setup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.entity.EnumRoleMapping;
import com.outlasttrialsstats.discordbot.entity.RankedRoleMapping;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.RoleTarget;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.RoleAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleMappingActionsTest {

    private static final String GUILD_ID = "guild-1";
    private static final String ROLE_ID = "role-1";
    private static final String ROLE_NAME = "Some Role";
    private static final RoleTarget PRESTIGE_10 = RoleTarget.parse(RoleCategory.PRESTIGE, "10").orElseThrow();

    @Mock
    private RoleMappingService roleMappingService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private RoleMappingActions actions;

    @Mock
    private Guild guild;

    @Mock
    private SelfMember selfMember;

    @Mock
    private Member invoker;

    @Mock
    private Role role;

    @Mock
    private RoleAction roleAction;

    @Mock
    private Consumer<String> reply;

    @Captor
    private ArgumentCaptor<Consumer<Role>> successCaptor;

    @Captor
    private ArgumentCaptor<Consumer<Throwable>> failureCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(guild.getId()).thenReturn(GUILD_ID);
        lenient().when(guild.getSelfMember()).thenReturn(selfMember);
        lenient().when(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        lenient().when(role.getId()).thenReturn(ROLE_ID);
        lenient().when(role.getName()).thenReturn(ROLE_NAME);
        lenient().when(role.getPermissions()).thenReturn(EnumSet.noneOf(Permission.class));
        lenient().when(selfMember.canInteract(role)).thenReturn(true);
        lenient().when(invoker.canInteract(role)).thenReturn(true);
    }

    @Test
    void linkSafeRole_savesMappingAndReplies() {
        when(roleMappingService.findOtherCategoryUsingRole(GUILD_ID, ROLE_ID, RoleCategory.PRESTIGE))
                .thenReturn(Optional.empty());
        when(messageService.getMessage(GUILD_ID, "setup.prestige_role.success", ROLE_NAME, 10)).thenReturn("ok");

        actions.add(guild, invoker, PRESTIGE_10, role, reply);

        verify(roleMappingService).saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, ROLE_ID, false);
        verify(reply).accept("ok");
    }

    @Test
    void linkEnumRole_savesEnumMapping() {
        RoleTarget steam = RoleTarget.parse(RoleCategory.PLATFORM, "STEAM").orElseThrow();
        when(roleMappingService.findOtherCategoryUsingRole(GUILD_ID, ROLE_ID, RoleCategory.PLATFORM))
                .thenReturn(Optional.empty());
        when(messageService.getMessage(GUILD_ID, "setup.role_mapping.success", ROLE_NAME, "Steam")).thenReturn("ok");

        actions.add(guild, invoker, steam, role, reply);

        verify(roleMappingService).saveEnumMapping(GUILD_ID, RoleCategory.PLATFORM, "STEAM", ROLE_ID, false);
        verify(reply).accept("ok");
    }

    @Test
    void linkPublicRole_rejected() {
        when(role.isPublicRole()).thenReturn(true);
        expectRejection("error.role_unsafe.public");
    }

    @Test
    void linkManagedRole_rejected() {
        when(role.isManaged()).thenReturn(true);
        expectRejection("error.role_unsafe.managed");
    }

    @Test
    void linkRoleAboveBot_rejected() {
        when(selfMember.canInteract(role)).thenReturn(false);
        expectRejection("error.role_unsafe.bot_hierarchy");
    }

    @Test
    void linkRoleAboveInvoker_rejected() {
        when(invoker.canInteract(role)).thenReturn(false);
        expectRejection("error.role_unsafe.invoker_hierarchy");
    }

    @Test
    void linkRoleWithAdministrator_rejected() {
        when(role.getPermissions()).thenReturn(EnumSet.of(Permission.ADMINISTRATOR));
        expectRejection("error.role_unsafe.dangerous_permissions");
    }

    @Test
    void linkRoleMappedInOtherCategory_rejected() {
        when(roleMappingService.findOtherCategoryUsingRole(GUILD_ID, ROLE_ID, RoleCategory.PRESTIGE))
                .thenReturn(Optional.of(RoleCategory.LEVEL));
        when(messageService.getMessage(GUILD_ID, "error.role_already_mapped",
                ROLE_NAME, RoleCategory.LEVEL.getDisplayName())).thenReturn("already mapped");

        actions.add(guild, invoker, PRESTIGE_10, role, reply);

        verify(reply).accept("already mapped");
        verify(roleMappingService, never()).saveRankedMapping(any(), any(), anyInt(), any(), anyBoolean());
    }

    @Test
    void botLacksManageRoles_rejected() {
        when(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(false);
        when(messageService.getMessage(GUILD_ID, "error.missing_permission.manage_roles")).thenReturn("no perm");

        actions.add(guild, invoker, PRESTIGE_10, role, reply);

        verify(reply).accept("no perm");
        verify(roleMappingService, never()).saveRankedMapping(any(), any(), anyInt(), any(), anyBoolean());
    }

    @Test
    void noRole_mappedRoleStillExists_reusesItInsteadOfCreating() {
        when(roleMappingService.findRankedMappingRoleId(GUILD_ID, RoleCategory.PRESTIGE, 10))
                .thenReturn(Optional.of(ROLE_ID));
        when(guild.getRoleById(ROLE_ID)).thenReturn(role);
        when(messageService.getMessage(GUILD_ID, "setup.prestige_role.success", ROLE_NAME, 10)).thenReturn("ok");

        actions.add(guild, invoker, PRESTIGE_10, null, reply);

        verify(guild, never()).createRole();
        verify(reply).accept("ok");
    }

    @Test
    void noRole_noMapping_createsRoleAndSavesMapping() {
        stubRoleCreation();
        Role created = mockCreatedRole();
        when(messageService.getMessage(GUILD_ID, "setup.prestige_role.success", "Prestige 10+", 10)).thenReturn("ok");

        actions.add(guild, invoker, PRESTIGE_10, null, reply);

        verify(roleAction).queue(successCaptor.capture(), failureCaptor.capture());
        successCaptor.getValue().accept(created);
        verify(roleMappingService).saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "created-id", true);
        verify(reply).accept("ok");
    }

    @Test
    void noRole_creationFails_repliesWithLocalizedError() {
        stubRoleCreation();
        when(messageService.getMessage(GUILD_ID, "setup.role_mapping.create_failed", "Prestige 10+"))
                .thenReturn("create failed");

        actions.add(guild, invoker, PRESTIGE_10, null, reply);

        verify(roleAction).queue(successCaptor.capture(), failureCaptor.capture());
        failureCaptor.getValue().accept(new RuntimeException("Maximum number of roles reached"));
        verify(reply).accept("create failed");
        verify(roleMappingService, never()).saveRankedMapping(any(), any(), anyInt(), any(), anyBoolean());
    }

    @Test
    @SuppressWarnings("unchecked")
    void noRole_savingMappingFails_deletesCreatedRoleAndRepliesWithError() {
        stubRoleCreation();
        Role created = mockCreatedRole();
        AuditableRestAction<Void> deleteAction = mock(AuditableRestAction.class);
        when(created.delete()).thenReturn(deleteAction);
        doThrow(new RuntimeException("db down")).when(roleMappingService)
                .saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "created-id", true);
        when(messageService.getMessage(GUILD_ID, "setup.role_mapping.create_failed", "Prestige 10+"))
                .thenReturn("create failed");

        actions.add(guild, invoker, PRESTIGE_10, null, reply);

        verify(roleAction).queue(successCaptor.capture(), failureCaptor.capture());
        successCaptor.getValue().accept(created);
        verify(deleteAction).queue(any(), any());
        verify(reply).accept("create failed");
    }

    @Test
    void linkRole_replacesOtherMappedRole_tellsAdminOldRoleStays() {
        Role oldRole = mock(Role.class);
        when(oldRole.getId()).thenReturn("old-id");
        when(oldRole.getName()).thenReturn("Old Role");
        when(roleMappingService.findRankedMappingRoleId(GUILD_ID, RoleCategory.PRESTIGE, 10))
                .thenReturn(Optional.of("old-id"));
        when(guild.getRoleById("old-id")).thenReturn(oldRole);
        when(roleMappingService.findOtherCategoryUsingRole(GUILD_ID, ROLE_ID, RoleCategory.PRESTIGE))
                .thenReturn(Optional.empty());
        when(messageService.getMessage(GUILD_ID, "setup.prestige_role.success", ROLE_NAME, 10)).thenReturn("ok");
        when(messageService.getMessage(GUILD_ID, "setup.role_mapping.old_role_kept", "Old Role")).thenReturn("note");

        actions.add(guild, invoker, PRESTIGE_10, role, reply);

        verify(roleMappingService).saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, ROLE_ID, false);
        verify(reply).accept("ok\nnote");
    }

    @Test
    void remove_roleStillExists_tellsAdminRoleStaysOnMembers() {
        when(roleMappingService.removeRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10))
                .thenReturn(Optional.of(ROLE_ID));
        when(guild.getRoleById(ROLE_ID)).thenReturn(role);
        when(messageService.getMessage(GUILD_ID, "setup.prestige_role.removed", 10)).thenReturn("removed");
        when(messageService.getMessage(GUILD_ID, "setup.role_mapping.old_role_kept", ROLE_NAME)).thenReturn("note");

        assertThat(actions.remove(guild, PRESTIGE_10)).isEqualTo("removed\nnote");
    }

    @Test
    void remove_noMapping_repliesWithoutNotice() {
        when(roleMappingService.removeRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10)).thenReturn(Optional.empty());
        when(messageService.getMessage(GUILD_ID, "setup.prestige_role.removed", 10)).thenReturn("removed");

        assertThat(actions.remove(guild, PRESTIGE_10)).isEqualTo("removed");
    }

    @Test
    void remove_connectedAccount_removesEnumMapping() {
        RoleTarget connected = RoleTarget.choices(RoleCategory.CONNECTED_ACCOUNT).getFirst();
        when(roleMappingService.removeEnumMapping(GUILD_ID, RoleCategory.CONNECTED_ACCOUNT, "verified"))
                .thenReturn(Optional.empty());
        when(messageService.getMessage(GUILD_ID, "setup.connected_account_role.removed")).thenReturn("removed");

        assertThat(actions.remove(guild, connected)).isEqualTo("removed");
    }

    @Test
    void list_combinesRankedAndEnumMappingsSortedByRank() {
        when(roleMappingService.getRankedMappings(GUILD_ID, RoleCategory.PRESTIGE)).thenReturn(List.of(
                new RankedRoleMapping(GUILD_ID, RoleCategory.PRESTIGE, 20, "r20"),
                new RankedRoleMapping(GUILD_ID, RoleCategory.PRESTIGE, 1, "r1")));
        when(roleMappingService.getEnumMappings(GUILD_ID, RoleCategory.PRESTIGE)).thenReturn(List.of());

        List<RoleMappingActions.Mapping> mappings = actions.list(GUILD_ID, RoleCategory.PRESTIGE);

        assertThat(mappings).extracting(m -> m.target().label()).containsExactly("Prestige 1+", "Prestige 20+");
        assertThat(mappings).extracting(RoleMappingActions.Mapping::roleId).containsExactly("r1", "r20");
    }

    @Test
    void list_skipsUnknownEnumValues() {
        when(roleMappingService.getRankedMappings(GUILD_ID, RoleCategory.PLATFORM)).thenReturn(List.of());
        when(roleMappingService.getEnumMappings(GUILD_ID, RoleCategory.PLATFORM)).thenReturn(List.of(
                new EnumRoleMapping(GUILD_ID, RoleCategory.PLATFORM, "STEAM", "steam"),
                new EnumRoleMapping(GUILD_ID, RoleCategory.PLATFORM, "SEGA", "sega")));

        assertThat(actions.list(GUILD_ID, RoleCategory.PLATFORM))
                .extracting(RoleMappingActions.Mapping::roleId).containsExactly("steam");
    }

    private void stubRoleCreation() {
        when(guild.createRole()).thenReturn(roleAction);
        when(roleAction.setName("Prestige 10+")).thenReturn(roleAction);
        when(roleAction.setPermissions(EnumSet.noneOf(Permission.class))).thenReturn(roleAction);
    }

    private Role mockCreatedRole() {
        Role created = mock(Role.class);
        when(created.getId()).thenReturn("created-id");
        when(created.getName()).thenReturn("Prestige 10+");
        return created;
    }

    private void expectRejection(String messageKey) {
        when(messageService.getMessage(GUILD_ID, messageKey, ROLE_NAME)).thenReturn("rejected");

        actions.add(guild, invoker, PRESTIGE_10, role, reply);

        verify(reply).accept("rejected");
        verify(roleMappingService, never()).saveRankedMapping(any(), any(), anyInt(), any(), anyBoolean());
        verify(roleMappingService, never()).findOtherCategoryUsingRole(any(), any(), any());
    }
}
