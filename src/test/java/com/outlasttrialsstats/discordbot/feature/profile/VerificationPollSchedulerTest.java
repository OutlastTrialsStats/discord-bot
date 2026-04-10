package com.outlasttrialsstats.discordbot.feature.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.DiscordRecentVerificationsResponse;
import com.outlasttrialsstats.backend.api.model.DiscordVerificationEntry;
import com.outlasttrialsstats.discordbot.feature.profile.dto.RoleAssignmentResult;
import com.outlasttrialsstats.discordbot.feature.profile.service.GuildSyncService;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerificationPollSchedulerTest {

    @Mock
    private JDA jda;

    @Mock
    private TOTStatsApiClient statsApiClient;

    @Mock
    private GuildSyncService guildSyncService;

    @InjectMocks
    private VerificationPollScheduler verificationPollScheduler;

    @Test
    void pollRecentVerifications_apiReturnsEmpty_doesNothing() {
        when(statsApiClient.getRecentVerifications(10)).thenReturn(Optional.empty());

        verificationPollScheduler.pollRecentVerifications();

        verify(jda, never()).getGuilds();
        verify(guildSyncService, never()).syncMember(any(), any());
    }

    @Test
    void pollRecentVerifications_noVerifications_doesNothing() {
        var response = new DiscordRecentVerificationsResponse();
        response.setVerifications(List.of());
        when(statsApiClient.getRecentVerifications(10)).thenReturn(Optional.of(response));

        verificationPollScheduler.pollRecentVerifications();

        verify(jda, never()).getGuilds();
        verify(guildSyncService, never()).syncMember(any(), any());
    }

    @Test
    void pollRecentVerifications_verifiedUser_syncsMemberInGuild() {
        var verification = new DiscordVerificationEntry();
        verification.setDiscordUserId("user-1");
        verification.setDisplayName("TestUser");

        var response = new DiscordRecentVerificationsResponse();
        response.setVerifications(List.of(verification));
        when(statsApiClient.getRecentVerifications(10)).thenReturn(Optional.of(response));

        var guild = mock(Guild.class);
        var member = mock(Member.class);
        when(jda.getGuilds()).thenReturn(List.of(guild));
        when(guild.getMemberById("user-1")).thenReturn(member);
        when(guildSyncService.syncMember(guild, member))
                .thenReturn(RoleAssignmentResult.of(List.of("Role1"), List.of()));

        verificationPollScheduler.pollRecentVerifications();

        verify(guildSyncService).syncMember(guild, member);
    }

    @Test
    void pollRecentVerifications_userNotInGuild_skips() {
        var verification = new DiscordVerificationEntry();
        verification.setDiscordUserId("user-1");
        verification.setDisplayName("TestUser");

        var response = new DiscordRecentVerificationsResponse();
        response.setVerifications(List.of(verification));
        when(statsApiClient.getRecentVerifications(10)).thenReturn(Optional.of(response));

        var guild = mock(Guild.class);
        when(jda.getGuilds()).thenReturn(List.of(guild));
        when(guild.getMemberById("user-1")).thenReturn(null);

        verificationPollScheduler.pollRecentVerifications();

        verify(guildSyncService, never()).syncMember(any(), any());
    }

    @Test
    void pollRecentVerifications_userInMultipleGuilds_syncsAll() {
        var verification = new DiscordVerificationEntry();
        verification.setDiscordUserId("user-1");
        verification.setDisplayName("TestUser");

        var response = new DiscordRecentVerificationsResponse();
        response.setVerifications(List.of(verification));
        when(statsApiClient.getRecentVerifications(10)).thenReturn(Optional.of(response));

        var guild1 = mock(Guild.class);
        var guild2 = mock(Guild.class);
        var member1 = mock(Member.class);
        var member2 = mock(Member.class);
        when(jda.getGuilds()).thenReturn(List.of(guild1, guild2));
        when(guild1.getMemberById("user-1")).thenReturn(member1);
        when(guild2.getMemberById("user-1")).thenReturn(member2);
        when(guildSyncService.syncMember(any(), any()))
                .thenReturn(RoleAssignmentResult.of(List.of(), List.of()));

        verificationPollScheduler.pollRecentVerifications();

        verify(guildSyncService).syncMember(guild1, member1);
        verify(guildSyncService).syncMember(guild2, member2);
    }
}
