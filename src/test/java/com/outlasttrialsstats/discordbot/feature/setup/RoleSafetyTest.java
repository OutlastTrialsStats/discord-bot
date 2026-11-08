package com.outlasttrialsstats.discordbot.feature.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleSafetyTest {

    @Mock
    private Member selfMember;

    @Mock
    private Member invoker;

    private Role role;

    @BeforeEach
    void setUp() {
        role = mock(Role.class);
        lenient().when(role.getPermissions()).thenReturn(EnumSet.of(Permission.MESSAGE_SEND, Permission.VIEW_CHANNEL));
        lenient().when(selfMember.canInteract(role)).thenReturn(true);
        lenient().when(invoker.canInteract(role)).thenReturn(true);
    }

    @Test
    void check_plainRole_isSafe() {
        assertThat(RoleSafety.check(role, selfMember, invoker)).isEmpty();
        assertThat(RoleSafety.isSafe(role, selfMember)).isTrue();
    }

    @Test
    void check_publicRole_rejected() {
        when(role.isPublicRole()).thenReturn(true);

        assertThat(RoleSafety.check(role, selfMember, invoker)).contains(RoleSafety.Violation.PUBLIC_ROLE);
    }

    @Test
    void check_managedRole_rejected() {
        when(role.isManaged()).thenReturn(true);

        assertThat(RoleSafety.check(role, selfMember, invoker)).contains(RoleSafety.Violation.MANAGED_ROLE);
    }

    @Test
    void check_roleAboveBot_rejected() {
        when(selfMember.canInteract(role)).thenReturn(false);

        assertThat(RoleSafety.check(role, selfMember, invoker)).contains(RoleSafety.Violation.BOT_CANNOT_INTERACT);
    }

    @Test
    void check_roleAboveInvoker_rejected() {
        when(invoker.canInteract(role)).thenReturn(false);

        assertThat(RoleSafety.check(role, selfMember, invoker)).contains(RoleSafety.Violation.INVOKER_CANNOT_INTERACT);
    }

    @Test
    void check_withoutInvoker_skipsInvokerHierarchy() {
        assertThat(RoleSafety.check(role, selfMember)).isEmpty();
        verify(invoker, never()).canInteract(role);
    }

    @ParameterizedTest
    @EnumSource(value = Permission.class, names = {
            "ADMINISTRATOR", "MANAGE_SERVER", "MANAGE_ROLES", "MANAGE_CHANNEL", "MANAGE_WEBHOOKS",
            "BAN_MEMBERS", "KICK_MEMBERS", "MODERATE_MEMBERS", "MESSAGE_MANAGE", "MESSAGE_MENTION_EVERYONE"
    })
    void check_dangerousPermission_rejected(Permission permission) {
        when(role.getPermissions()).thenReturn(EnumSet.of(Permission.MESSAGE_SEND, permission));

        assertThat(RoleSafety.check(role, selfMember, invoker)).contains(RoleSafety.Violation.DANGEROUS_PERMISSIONS);
        assertThat(RoleSafety.isSafe(role, selfMember)).isFalse();
    }

    @Test
    void violations_haveMessageKeys() {
        for (RoleSafety.Violation violation : RoleSafety.Violation.values()) {
            assertThat(violation.getMessageKey()).startsWith("error.role_unsafe.");
        }
    }
}
