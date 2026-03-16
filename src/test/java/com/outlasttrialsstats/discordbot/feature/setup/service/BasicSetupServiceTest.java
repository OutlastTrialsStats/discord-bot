package com.outlasttrialsstats.discordbot.feature.setup.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.RoleConfig;
import java.awt.Color;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
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
    private BiConsumer<Integer, List<String>> onComplete;

    @Mock
    private Runnable onNoChanges;

    @Captor
    private ArgumentCaptor<Consumer<Role>> callbackCaptor;

    private static final String GUILD_ID = "guild-1";

    @BeforeEach
    void setUp() {
        when(guild.getId()).thenReturn(GUILD_ID);
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
                    eq(GUILD_ID), eq(RoleCategory.PRESTIGE), eq(threshold), eq("role-" + threshold));
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
        verify(onComplete, never()).accept(any(), any());
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

        verify(roleAction, atLeastOnce()).queue(callbackCaptor.capture());

        List<Consumer<Role>> callbacks = callbackCaptor.getAllValues();
        for (int i = 0; i < callbacks.size(); i++) {
            Role createdRole = mockRole("created-" + i, "Role " + i);
            callbacks.get(i).accept(createdRole);
        }

        verify(onComplete).accept(eq(callbacks.size()), any());
    }

    private Role mockRole(String id, String name) {
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(id);
        when(role.getName()).thenReturn(name);
        return role;
    }
}
