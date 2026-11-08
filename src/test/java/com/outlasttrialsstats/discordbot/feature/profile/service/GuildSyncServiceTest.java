package com.outlasttrialsstats.discordbot.feature.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.DiscordBulkProfileEntry;
import com.outlasttrialsstats.backend.api.model.DiscordBulkProfileResponse;
import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.feature.profile.dto.GuildSyncResult;
import com.outlasttrialsstats.discordbot.feature.profile.dto.RoleAssignmentResult;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuildSyncServiceTest {

    private static final String GUILD_ID = "guild-1";

    @Mock
    private RoleAssignmentService roleAssignmentService;

    @Mock
    private TOTStatsApiClient statsApiClient;

    @Mock
    private GuildServerRepository guildServerRepository;

    @InjectMocks
    private GuildSyncService guildSyncService;

    @Mock
    private Guild guild;

    @BeforeEach
    void setUp() {
        lenient().when(guild.getId()).thenReturn(GUILD_ID);
    }

    @Test
    void syncMember_bot_isSkipped() {
        var bot = mockMember("bot-1", true);

        var result = guildSyncService.syncMember(guild, bot);

        assertThat(result.verified()).isFalse();
        verifyNoInteractions(roleAssignmentService);
    }

    @Test
    void guildSyncGuard_onlyOneSyncPerGuild() {
        assertThat(guildSyncService.tryStartGuildSync(GUILD_ID)).isTrue();
        assertThat(guildSyncService.tryStartGuildSync(GUILD_ID)).isFalse();
        assertThat(guildSyncService.tryStartGuildSync("guild-2")).isTrue();

        guildSyncService.finishGuildSync(GUILD_ID);

        assertThat(guildSyncService.tryStartGuildSync(GUILD_ID)).isTrue();
    }

    @Test
    void syncMembers_countsFoundNotLinkedAndFailedSeparately() {
        var updated = mockMember("user-1", false);
        var unchanged = mockMember("user-2", false);
        var unverified = mockMember("user-3", false);
        var throwing = mockMember("user-4", false);
        when(statsApiClient.getBulkProfiles(anyList())).thenReturn(Optional.of(bulkResponse(
                entry("user-1"), entry("user-2"), entry("user-4"))));
        when(roleAssignmentService.assignRolesFromProfile(eq(guild), eq(updated), any()))
                .thenReturn(RoleAssignmentResult.of(List.of("Role"), List.of()));
        when(roleAssignmentService.assignRolesFromProfile(eq(guild), eq(unchanged), any()))
                .thenReturn(RoleAssignmentResult.of(List.of(), List.of()));
        when(roleAssignmentService.assignRolesFromProfile(eq(guild), eq(throwing), any()))
                .thenThrow(new IllegalStateException("boom"));

        var result = guildSyncService.syncMembers(guild, List.of(updated, unchanged, unverified, throwing));

        assertThat(result).isEqualTo(new GuildSyncResult(1, 1, 1, 1));
        verify(roleAssignmentService).cleanupUnverifiedMember(guild, unverified);
    }

    @Test
    void syncMembers_apiFails_countsAsFailedAndLeavesRolesUntouched() {
        var member = mockMember("user-1", false);
        when(statsApiClient.getBulkProfiles(List.of("user-1"))).thenReturn(Optional.empty());

        var result = guildSyncService.syncMembers(guild, List.of(member));

        assertThat(result).isEqualTo(new GuildSyncResult(0, 0, 0, 1));
        verify(roleAssignmentService, never()).assignRolesFromProfile(any(), any(), any());
        verify(roleAssignmentService, never()).cleanupUnverifiedMember(any(), any());
        verify(guildServerRepository, never()).save(any());
    }

    @Test
    void syncMembers_nullProfileList_isTreatedAsFailure() {
        var member = mockMember("user-1", false);
        when(statsApiClient.getBulkProfiles(List.of("user-1")))
                .thenReturn(Optional.of(new DiscordBulkProfileResponse().profiles(null)));

        var result = guildSyncService.syncMembers(guild, List.of(member));

        assertThat(result.failed()).isEqualTo(1);
        verify(roleAssignmentService, never()).cleanupUnverifiedMember(any(), any());
    }

    @Test
    void syncMembers_oneBatchFails_doesNotOverwriteVerifiedCountOrCleanUpFailedBatch() {
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            members.add(mockMember("user-" + i, false));
        }
        when(statsApiClient.getBulkProfiles(anyList()))
                .thenReturn(Optional.of(bulkResponse(entry("user-0"))))
                .thenReturn(Optional.empty());
        when(roleAssignmentService.assignRolesFromProfile(any(), any(), any()))
                .thenReturn(RoleAssignmentResult.of(List.of(), List.of()));

        var result = guildSyncService.syncMembers(guild, members);

        // user-1..user-99 are confirmed missing in batch 1; batch 2 (user-100..149) failed entirely
        assertThat(result).isEqualTo(new GuildSyncResult(0, 1, 99, 50));
        verify(roleAssignmentService, times(99)).cleanupUnverifiedMember(eq(guild), any());
        for (int i = 100; i < 150; i++) {
            verify(roleAssignmentService, never()).cleanupUnverifiedMember(guild, members.get(i));
        }
        verify(guildServerRepository, never()).save(any());
    }

    @Test
    void syncMembers_allBatchesSucceed_savesVerifiedCount() {
        var member = mockMember("user-1", false);
        when(statsApiClient.getBulkProfiles(List.of("user-1"))).thenReturn(Optional.of(bulkResponse(entry("user-1"))));
        when(roleAssignmentService.assignRolesFromProfile(any(), any(), any()))
                .thenReturn(RoleAssignmentResult.of(List.of(), List.of()));
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.empty());

        guildSyncService.syncMembers(guild, List.of(member));

        var serverCaptor = ArgumentCaptor.forClass(GuildServer.class);
        verify(guildServerRepository).save(serverCaptor.capture());
        assertThat(serverCaptor.getValue().getVerifiedMemberCount()).isEqualTo(1);
    }

    @Test
    void syncMembers_verifiedMember_assignsRolesWithMappedProfile() {
        var member = mockMember("user-1", false);
        var entry = entry("user-1");
        entry.setPrestigeLevel(10);
        entry.setLevel(50);
        entry.setDisplayName("Player");
        when(statsApiClient.getBulkProfiles(List.of("user-1"))).thenReturn(Optional.of(bulkResponse(entry)));
        when(roleAssignmentService.assignRolesFromProfile(any(), any(), any()))
                .thenReturn(RoleAssignmentResult.of(List.of("Prestige"), List.of()));

        guildSyncService.syncMembers(guild, List.of(member));

        var profileCaptor = ArgumentCaptor.forClass(DiscordProfileResponse.class);
        verify(roleAssignmentService).assignRolesFromProfile(eq(guild), eq(member), profileCaptor.capture());
        var profile = profileCaptor.getValue();
        assertThat(profile.getPrestigeLevel()).isEqualTo(10);
        assertThat(profile.getLevel()).isEqualTo(50);
        assertThat(profile.getDisplayName()).isEqualTo("Player");
    }

    @Test
    void syncMembers_duplicateAndNullIdsInResponse_areTolerated() {
        var member = mockMember("user-1", false);
        var first = entry("user-1");
        first.setLevel(1);
        var duplicate = entry("user-1");
        duplicate.setLevel(2);
        var nullId = entry(null);
        when(statsApiClient.getBulkProfiles(List.of("user-1")))
                .thenReturn(Optional.of(bulkResponse(first, nullId, duplicate, null)));
        when(roleAssignmentService.assignRolesFromProfile(any(), any(), any()))
                .thenReturn(RoleAssignmentResult.of(List.of(), List.of()));

        var result = guildSyncService.syncMembers(guild, List.of(member));

        assertThat(result).isEqualTo(new GuildSyncResult(0, 1, 0, 0));
        var profileCaptor = ArgumentCaptor.forClass(DiscordProfileResponse.class);
        verify(roleAssignmentService).assignRolesFromProfile(eq(guild), eq(member), profileCaptor.capture());
        assertThat(profileCaptor.getValue().getLevel()).isEqualTo(1);
    }

    @Test
    void syncMembers_botMembers_excludedFromApiCallAndCounts() {
        var bot = mockMember("bot-1", true);
        var human = mockMember("user-1", false);
        when(statsApiClient.getBulkProfiles(List.of("user-1"))).thenReturn(Optional.of(bulkResponse()));

        var result = guildSyncService.syncMembers(guild, List.of(bot, human));

        assertThat(result).isEqualTo(new GuildSyncResult(0, 0, 1, 0));
        verify(roleAssignmentService, never()).cleanupUnverifiedMember(guild, bot);
    }

    @Test
    void syncMembers_moreThan100Members_batchesRequests() {
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            members.add(mockMember("user-" + i, false));
        }
        when(statsApiClient.getBulkProfiles(anyList())).thenReturn(Optional.of(bulkResponse()));

        guildSyncService.syncMembers(guild, members);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(statsApiClient, times(2)).getBulkProfiles(idsCaptor.capture());
        assertThat(idsCaptor.getAllValues().get(0)).hasSize(GuildSyncService.BULK_BATCH_SIZE);
        assertThat(idsCaptor.getAllValues().get(1)).hasSize(50);
    }

    private Member mockMember(String id, boolean isBot) {
        var member = mock(Member.class);
        var user = mock(User.class);
        lenient().when(member.getId()).thenReturn(id);
        when(member.getUser()).thenReturn(user);
        when(user.isBot()).thenReturn(isBot);
        return member;
    }

    private DiscordBulkProfileResponse bulkResponse(DiscordBulkProfileEntry... entries) {
        var response = new DiscordBulkProfileResponse();
        response.setProfiles(new ArrayList<>(Arrays.asList(entries)));
        return response;
    }

    private DiscordBulkProfileEntry entry(String discordUserId) {
        var entry = new DiscordBulkProfileEntry();
        entry.setDiscordUserId(discordUserId);
        entry.setProfileId(UUID.randomUUID());
        return entry;
    }
}
