package com.outlasttrialsstats.discordbot.feature.setup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.AccountCreationType;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.RoleConfig;
import java.awt.Color;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.requests.restaction.RoleAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Captor;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BasicSetupServiceTest {

    @Mock
    private RoleMappingService roleMappingService;

    @InjectMocks
    private BasicSetupService basicSetupService;

    @Mock
    private Guild guild;

    @Mock
    private Consumer<BasicSetupService.SetupResult> onComplete;

    @Mock
    private Runnable onNoChanges;

    @Captor
    private ArgumentCaptor<Consumer<Role>> callbackCaptor;

    @Captor
    private ArgumentCaptor<Consumer<Throwable>> failureCaptor;

    @Captor
    private ArgumentCaptor<BasicSetupService.SetupResult> resultCaptor;

    private static final String GUILD_ID = "guild-1";

    @Mock
    private SelfMember selfMember;

    @BeforeEach
    void setUp() {
        when(guild.getId()).thenReturn(GUILD_ID);
        lenient().when(guild.getSelfMember()).thenReturn(selfMember);
        lenient().when(selfMember.canInteract(any(Role.class))).thenReturn(true);
    }

    @Test
    void setupRoles_roleAlreadyExists_linksMappingWithoutCreating() {
        List<Role> existingRoles = Arrays.stream(RoleConfig.PRESTIGE_THRESHOLDS)
                .mapToObj(t -> mockRole("role-" + t, "Prestige " + t + "+"))
                .collect(Collectors.toList());

        when(guild.getRoles()).thenReturn(existingRoles);

        basicSetupService.setupRoles(guild, Set.of(RoleCategory.PRESTIGE), onComplete, onNoChanges);

        for (int threshold : RoleConfig.PRESTIGE_THRESHOLDS) {
            verify(roleMappingService).saveRankedMapping(
                    eq(GUILD_ID), eq(RoleCategory.PRESTIGE), eq(threshold), eq("role-" + threshold), eq(false));
        }
        verify(guild, never()).createRole();
    }

    @Test
    void setupRoles_roleDoesNotExist_createsRole() {
        when(guild.getRoles()).thenReturn(List.of());

        RoleAction roleAction = mock(RoleAction.class);
        when(guild.createRole()).thenReturn(roleAction);
        when(roleAction.setName(anyString())).thenReturn(roleAction);
        when(roleAction.setHoisted(true)).thenReturn(roleAction);
        when(roleAction.setPermissions(EnumSet.noneOf(Permission.class))).thenReturn(roleAction);

        basicSetupService.setupRoles(guild, Set.of(RoleCategory.PRESTIGE), onComplete, onNoChanges);

        verify(guild, times(RoleConfig.PRESTIGE_THRESHOLDS.length)).createRole();
    }

    @Test
    void setupRoles_noNewRoles_callsOnNoChanges() {
        List<Role> existingRoles = Arrays.stream(RoleConfig.PRESTIGE_THRESHOLDS)
                .mapToObj(t -> mockRole("role-" + t, "Prestige " + t + "+"))
                .collect(Collectors.toList());

        when(guild.getRoles()).thenReturn(existingRoles);

        basicSetupService.setupRoles(guild, Set.of(RoleCategory.PRESTIGE), onComplete, onNoChanges);

        verify(onNoChanges).run();
        verify(onComplete, never()).accept(any());
    }

    @Test
    void setupRoles_enumCategory_createsWithColor() {
        when(guild.getRoles()).thenReturn(List.of());

        RoleAction roleAction = mock(RoleAction.class);
        when(guild.createRole()).thenReturn(roleAction);
        when(roleAction.setName(anyString())).thenReturn(roleAction);
        when(roleAction.setHoisted(false)).thenReturn(roleAction);
        when(roleAction.setPermissions(EnumSet.noneOf(Permission.class))).thenReturn(roleAction);
        when(roleAction.setColor(any(Color.class))).thenReturn(roleAction);

        basicSetupService.setupRoles(guild, Set.of(RoleCategory.REAGENT_RIG), onComplete, onNoChanges);

        verify(roleAction, atLeastOnce()).setColor(any(Color.class));
    }

    @Test
    void setupRoles_roleCreated_callbackSavesMappingAndCompletesWhenDone() {
        when(guild.getRoles()).thenReturn(List.of());

        RoleAction roleAction = mock(RoleAction.class);
        when(guild.createRole()).thenReturn(roleAction);
        when(roleAction.setName(anyString())).thenReturn(roleAction);
        when(roleAction.setHoisted(false)).thenReturn(roleAction);
        when(roleAction.setPermissions(EnumSet.noneOf(Permission.class))).thenReturn(roleAction);

        basicSetupService.setupRoles(guild, Set.of(RoleCategory.ACCOUNT_TYPE), onComplete, onNoChanges);

        verify(roleAction, atLeastOnce()).queue(callbackCaptor.capture(), failureCaptor.capture());

        List<Consumer<Role>> callbacks = callbackCaptor.getAllValues();
        for (int i = 0; i < callbacks.size(); i++) {
            Role createdRole = mockRole("created-" + i, "Role " + i);
            callbacks.get(i).accept(createdRole);
        }

        verify(onComplete).accept(resultCaptor.capture());
        assertThat(resultCaptor.getValue().createdRoles()).hasSize(callbacks.size());
        assertThat(resultCaptor.getValue().failedRoles()).isEmpty();
        for (int i = 0; i < callbacks.size(); i++) {
            verify(roleMappingService).saveEnumMapping(
                    eq(GUILD_ID), eq(RoleCategory.ACCOUNT_TYPE), anyString(), eq("created-" + i), eq(true));
        }
    }

    @Test
    void setupRoles_nameMatchedRoleIsPrivileged_createsNewRoleInstead() {
        Role adminRole = mock(Role.class);
        when(adminRole.getName()).thenReturn("Account Connected");
        when(adminRole.getPermissions()).thenReturn(EnumSet.of(Permission.ADMINISTRATOR));
        when(guild.getRoles()).thenReturn(List.of(adminRole));

        RoleAction roleAction = mock(RoleAction.class);
        when(guild.createRole()).thenReturn(roleAction);
        when(roleAction.setName("Account Connected")).thenReturn(roleAction);
        when(roleAction.setHoisted(false)).thenReturn(roleAction);
        when(roleAction.setPermissions(EnumSet.noneOf(Permission.class))).thenReturn(roleAction);

        basicSetupService.setupRoles(guild, Set.of(RoleCategory.CONNECTED_ACCOUNT), onComplete, onNoChanges);

        verify(guild).createRole();
        verify(adminRole, never()).getId();
        verify(roleMappingService, never()).saveEnumMapping(any(), any(), any(), any(), anyBoolean());
        verify(onNoChanges, never()).run();
    }

    @Test
    void setupRoles_nameMatchedRoleAboveBot_createsNewRoleInstead() {
        Role highRole = mock(Role.class);
        when(highRole.getName()).thenReturn("Account Connected");
        when(selfMember.canInteract(highRole)).thenReturn(false);
        when(guild.getRoles()).thenReturn(List.of(highRole));

        RoleAction roleAction = mock(RoleAction.class);
        when(guild.createRole()).thenReturn(roleAction);
        when(roleAction.setName("Account Connected")).thenReturn(roleAction);
        when(roleAction.setHoisted(false)).thenReturn(roleAction);
        when(roleAction.setPermissions(EnumSet.noneOf(Permission.class))).thenReturn(roleAction);

        basicSetupService.setupRoles(guild, Set.of(RoleCategory.CONNECTED_ACCOUNT), onComplete, onNoChanges);

        verify(guild).createRole();
        verify(roleMappingService, never()).saveEnumMapping(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void setupRoles_creationFails_reportsFailedRoleWhenAllDone() {
        when(guild.getRoles()).thenReturn(List.of());
        RoleAction roleAction = stubRoleAction();

        basicSetupService.setupRoles(guild, Set.of(RoleCategory.ACCOUNT_TYPE), onComplete, onNoChanges);

        verify(roleAction, atLeastOnce()).queue(callbackCaptor.capture(), failureCaptor.capture());
        List<Consumer<Role>> callbacks = callbackCaptor.getAllValues();
        List<Consumer<Throwable>> failures = failureCaptor.getAllValues();
        assertThat(callbacks).hasSizeGreaterThan(1);

        failures.get(0).accept(new RuntimeException("role limit reached"));
        verify(onComplete, never()).accept(any());
        for (int i = 1; i < callbacks.size(); i++) {
            callbacks.get(i).accept(mockRole("created-" + i, "Role " + i));
        }

        verify(onComplete).accept(resultCaptor.capture());
        assertThat(resultCaptor.getValue().failedRoles()).hasSize(1);
        assertThat(resultCaptor.getValue().createdRoles()).hasSize(callbacks.size() - 1);
        verify(onNoChanges, never()).run();
    }

    @Test
    void setupRoles_callbacksRunSynchronously_completesExactlyOnceAfterLoop() {
        when(guild.getRoles()).thenReturn(List.of());
        RoleAction roleAction = stubRoleAction();
        doAnswer(invocation -> {
            Consumer<Role> success = invocation.getArgument(0);
            success.accept(mockRole("created", "Created"));
            return null;
        }).when(roleAction).queue(any(), any());

        basicSetupService.setupRoles(guild, Set.of(RoleCategory.ACCOUNT_TYPE), onComplete, onNoChanges);

        verify(onComplete, times(1)).accept(resultCaptor.capture());
        int expectedRoles = (int) Arrays.stream(AccountCreationType.values())
                .filter(t -> RoleConfig.ACCOUNT_TYPE_NAMES.get(t) != null)
                .count();
        assertThat(resultCaptor.getValue().createdRoles()).hasSize(expectedRoles);
    }

    @Test
    void setupRoles_savingMappingFails_reportsRoleAsFailed() {
        when(guild.getRoles()).thenReturn(List.of());
        RoleAction roleAction = stubRoleAction();
        doThrow(new RuntimeException("db down")).when(roleMappingService)
                .saveEnumMapping(any(), any(), any(), any(), anyBoolean());

        basicSetupService.setupRoles(guild, Set.of(RoleCategory.CONNECTED_ACCOUNT), onComplete, onNoChanges);

        verify(roleAction).queue(callbackCaptor.capture(), failureCaptor.capture());
        callbackCaptor.getValue().accept(mockRole("created", "Account Connected"));

        verify(onComplete).accept(resultCaptor.capture());
        assertThat(resultCaptor.getValue().failedRoles()).containsExactly("Account Connected");
        assertThat(resultCaptor.getValue().createdRoles()).isEmpty();
    }

    @Test
    void setupRoles_createRoleThrows_reportsRoleAsFailedAndCompletes() {
        when(guild.getRoles()).thenReturn(List.of());
        when(guild.createRole()).thenThrow(new RuntimeException("missing permission"));

        basicSetupService.setupRoles(guild, Set.of(RoleCategory.CONNECTED_ACCOUNT), onComplete, onNoChanges);

        verify(onComplete).accept(resultCaptor.capture());
        assertThat(resultCaptor.getValue().failedRoles()).containsExactly("Account Connected");
        verify(onNoChanges, never()).run();
    }

    private RoleAction stubRoleAction() {
        RoleAction roleAction = mock(RoleAction.class);
        when(guild.createRole()).thenReturn(roleAction);
        when(roleAction.setName(anyString())).thenReturn(roleAction);
        when(roleAction.setHoisted(false)).thenReturn(roleAction);
        when(roleAction.setPermissions(EnumSet.noneOf(Permission.class))).thenReturn(roleAction);
        return roleAction;
    }

    private Role mockRole(String id, String name) {
        Role role = mock(Role.class);
        lenient().when(role.getId()).thenReturn(id);
        lenient().when(role.getName()).thenReturn(name);
        lenient().when(role.getPermissions()).thenReturn(EnumSet.noneOf(Permission.class));
        return role;
    }
}
