package com.outlasttrialsstats.discordbot.feature.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerificationPollSchedulerTest {

    private static final long USER_ID = 123456789012345678L;
    private static final String USER_ID_STR = String.valueOf(USER_ID);

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
        when(statsApiClient.getRecentVerifications(VerificationPollScheduler.POLL_WINDOW_SECONDS))
                .thenReturn(Optional.empty());

        verificationPollScheduler.pollRecentVerifications();

        verify(jda, never()).getGuilds();
        verify(guildSyncService, never()).syncMember(any(), any());
    }

    @Test
    void pollRecentVerifications_noVerifications_doesNothing() {
        var response = new DiscordRecentVerificationsResponse();
        response.setVerifications(List.of());
        when(statsApiClient.getRecentVerifications(VerificationPollScheduler.POLL_WINDOW_SECONDS))
                .thenReturn(Optional.of(response));

        verificationPollScheduler.pollRecentVerifications();

        verify(jda, never()).getGuilds();
        verify(guildSyncService, never()).syncMember(any(), any());
    }

    @Test
    void pollRecentVerifications_verifiedUser_retrievesMemberAndSyncs() {
        mockVerification(USER_ID_STR);
        var guild = mock(Guild.class);
        var member = mock(Member.class);
        when(jda.getGuilds()).thenReturn(List.of(guild));
        stubRetrieve(guild, member);
        when(guildSyncService.syncMember(guild, member))
                .thenReturn(RoleAssignmentResult.of(List.of("Role1"), List.of()));

        verificationPollScheduler.pollRecentVerifications();

        verify(guild).retrieveMemberById(USER_ID);
        verify(guild, never()).getMemberById(anyString());
        verify(guildSyncService).syncMember(guild, member);
    }

    @Test
    void pollRecentVerifications_userNotInAnyGuild_markedProcessed() {
        mockVerification(USER_ID_STR);
        var guild = mock(Guild.class);
        when(jda.getGuilds()).thenReturn(List.of(guild));
        stubRetrieveFailure(guild, ErrorResponse.UNKNOWN_MEMBER);

        verificationPollScheduler.pollRecentVerifications();
        verificationPollScheduler.pollRecentVerifications();

        verify(guild, times(1)).retrieveMemberById(USER_ID);
        verify(guildSyncService, never()).syncMember(any(), any());
    }

    @Test
    void pollRecentVerifications_userInMultipleGuilds_syncsAll() {
        mockVerification(USER_ID_STR);
        var guild1 = mock(Guild.class);
        var guild2 = mock(Guild.class);
        var member1 = mock(Member.class);
        var member2 = mock(Member.class);
        when(jda.getGuilds()).thenReturn(List.of(guild1, guild2));
        stubRetrieve(guild1, member1);
        stubRetrieve(guild2, member2);
        when(guildSyncService.syncMember(any(), any()))
                .thenReturn(RoleAssignmentResult.of(List.of(), List.of()));

        verificationPollScheduler.pollRecentVerifications();

        verify(guildSyncService).syncMember(guild1, member1);
        verify(guildSyncService).syncMember(guild2, member2);
    }

    @Test
    void pollRecentVerifications_unknownMemberInOneGuild_stillSyncsOtherGuild() {
        mockVerification(USER_ID_STR);
        var guild1 = mock(Guild.class);
        var guild2 = mock(Guild.class);
        var member2 = mock(Member.class);
        when(jda.getGuilds()).thenReturn(List.of(guild1, guild2));
        stubRetrieveFailure(guild1, ErrorResponse.UNKNOWN_MEMBER);
        stubRetrieve(guild2, member2);
        when(guildSyncService.syncMember(guild2, member2))
                .thenReturn(RoleAssignmentResult.of(List.of(), List.of()));

        verificationPollScheduler.pollRecentVerifications();
        verificationPollScheduler.pollRecentVerifications();

        verify(guildSyncService, times(1)).syncMember(guild2, member2);
        verify(guildSyncService, never()).syncMember(eq(guild1), any());
    }

    @Test
    void pollRecentVerifications_sameUserTwice_onlySyncsOnce() {
        mockVerification(USER_ID_STR);
        var guild = mock(Guild.class);
        var member = mock(Member.class);
        when(jda.getGuilds()).thenReturn(List.of(guild));
        stubRetrieve(guild, member);
        when(guildSyncService.syncMember(guild, member))
                .thenReturn(RoleAssignmentResult.of(List.of(), List.of()));

        verificationPollScheduler.pollRecentVerifications();
        verificationPollScheduler.pollRecentVerifications();

        verify(guildSyncService, times(1)).syncMember(guild, member);
    }

    @Test
    void pollRecentVerifications_lookupFailed_retriesOnNextPoll() {
        mockVerification(USER_ID_STR);
        var guild = mock(Guild.class);
        var member = mock(Member.class);
        when(jda.getGuilds()).thenReturn(List.of(guild));
        stubRetrieve(guild, member);
        when(guildSyncService.syncMember(guild, member))
                .thenReturn(RoleAssignmentResult.lookupFailed())
                .thenReturn(RoleAssignmentResult.of(List.of(), List.of()));

        verificationPollScheduler.pollRecentVerifications();
        verificationPollScheduler.pollRecentVerifications();
        verificationPollScheduler.pollRecentVerifications();

        verify(guildSyncService, times(2)).syncMember(guild, member);
    }

    @Test
    void pollRecentVerifications_retrieveFailsWithOtherError_retriesOnNextPoll() {
        mockVerification(USER_ID_STR);
        var guild = mock(Guild.class);
        var member = mock(Member.class);
        when(jda.getGuilds()).thenReturn(List.of(guild));
        CacheRestAction<Member> action = mockAction();
        when(guild.retrieveMemberById(USER_ID)).thenReturn(action);
        ErrorResponseException serverError = errorResponse(ErrorResponse.SERVER_ERROR);
        when(action.complete())
                .thenThrow(serverError)
                .thenReturn(member);
        when(guildSyncService.syncMember(guild, member))
                .thenReturn(RoleAssignmentResult.of(List.of(), List.of()));

        verificationPollScheduler.pollRecentVerifications();
        verify(guildSyncService, never()).syncMember(any(), any());

        verificationPollScheduler.pollRecentVerifications();
        verificationPollScheduler.pollRecentVerifications();

        verify(guildSyncService, times(1)).syncMember(guild, member);
    }

    @Test
    void pollRecentVerifications_retrieveThrowsRuntimeException_retriesOnNextPoll() {
        mockVerification(USER_ID_STR);
        var guild = mock(Guild.class);
        when(jda.getGuilds()).thenReturn(List.of(guild));
        CacheRestAction<Member> action = mockAction();
        when(guild.retrieveMemberById(USER_ID)).thenReturn(action);
        when(action.complete()).thenThrow(new RuntimeException("timeout"));

        verificationPollScheduler.pollRecentVerifications();
        verificationPollScheduler.pollRecentVerifications();

        verify(action, times(2)).complete();
        verify(guildSyncService, never()).syncMember(any(), any());
    }

    @Test
    void pollRecentVerifications_syncThrows_retriesOnNextPoll() {
        mockVerification(USER_ID_STR);
        var guild = mock(Guild.class);
        var member = mock(Member.class);
        when(jda.getGuilds()).thenReturn(List.of(guild));
        stubRetrieve(guild, member);
        when(guildSyncService.syncMember(guild, member))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(RoleAssignmentResult.of(List.of(), List.of()));

        verificationPollScheduler.pollRecentVerifications();
        verificationPollScheduler.pollRecentVerifications();
        verificationPollScheduler.pollRecentVerifications();

        verify(guildSyncService, times(2)).syncMember(guild, member);
    }

    @Test
    void pollRecentVerifications_invalidUserId_skippedWithoutGuildLookup() {
        mockVerification("not-a-snowflake");

        verificationPollScheduler.pollRecentVerifications();
        verificationPollScheduler.pollRecentVerifications();

        verify(jda, never()).getGuilds();
        verify(guildSyncService, never()).syncMember(any(), any());
    }

    private void mockVerification(String discordUserId) {
        var verification = new DiscordVerificationEntry();
        verification.setDiscordUserId(discordUserId);
        verification.setDisplayName("TestUser");
        var response = new DiscordRecentVerificationsResponse();
        response.setVerifications(List.of(verification));
        when(statsApiClient.getRecentVerifications(VerificationPollScheduler.POLL_WINDOW_SECONDS))
                .thenReturn(Optional.of(response));
    }

    @SuppressWarnings("unchecked")
    private static CacheRestAction<Member> mockAction() {
        return mock(CacheRestAction.class);
    }

    private static void stubRetrieve(Guild guild, Member member) {
        CacheRestAction<Member> action = mockAction();
        when(guild.retrieveMemberById(USER_ID)).thenReturn(action);
        when(action.complete()).thenReturn(member);
    }

    private static void stubRetrieveFailure(Guild guild, ErrorResponse errorResponse) {
        CacheRestAction<Member> action = mockAction();
        when(guild.retrieveMemberById(USER_ID)).thenReturn(action);
        ErrorResponseException exception = errorResponse(errorResponse);
        when(action.complete()).thenThrow(exception);
    }

    private static ErrorResponseException errorResponse(ErrorResponse errorResponse) {
        ErrorResponseException exception = mock(ErrorResponseException.class);
        when(exception.getErrorResponse()).thenReturn(errorResponse);
        return exception;
    }
}
