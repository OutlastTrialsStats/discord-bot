package com.outlasttrialsstats.discordbot.feature.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.feature.profile.dto.GuildSyncResult;
import com.outlasttrialsstats.discordbot.feature.profile.dto.RoleAssignmentResult;
import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuildSyncServiceTest {

    @Mock
    private RoleAssignmentService roleAssignmentService;

    @InjectMocks
    private GuildSyncService guildSyncService;

    @Mock
    private Guild guild;

    @Test
    void syncMembers_countsLookupFailuresAsFailedNotUnverified() {
        var updated = humanMember();
        var unchanged = humanMember();
        var unverified = humanMember();
        var lookupFailed = humanMember();
        var throwing = humanMember();
        when(roleAssignmentService.assignRoles(guild, updated))
                .thenReturn(RoleAssignmentResult.of(List.of("Role"), List.of()));
        when(roleAssignmentService.assignRoles(guild, unchanged))
                .thenReturn(RoleAssignmentResult.of(List.of(), List.of()));
        when(roleAssignmentService.assignRoles(guild, unverified))
                .thenReturn(RoleAssignmentResult.notVerified());
        when(roleAssignmentService.assignRoles(guild, lookupFailed))
                .thenReturn(RoleAssignmentResult.lookupFailed());
        when(roleAssignmentService.assignRoles(guild, throwing))
                .thenThrow(new IllegalStateException("boom"));

        var result = guildSyncService.syncMembers(guild,
                List.of(updated, unchanged, unverified, lookupFailed, throwing));

        assertThat(result).isEqualTo(new GuildSyncResult(1, 1, 1, 2));
    }

    private Member humanMember() {
        var member = mock(Member.class);
        var user = mock(User.class);
        when(member.getUser()).thenReturn(user);
        when(user.isBot()).thenReturn(false);
        return member;
    }
}
