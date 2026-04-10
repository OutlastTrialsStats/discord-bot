package com.outlasttrialsstats.discordbot.feature.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.DiscordBulkProfileEntry;
import com.outlasttrialsstats.backend.api.model.DiscordBulkProfileResponse;
import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import com.outlasttrialsstats.discordbot.feature.profile.dto.RoleAssignmentResult;
import com.outlasttrialsstats.discordbot.feature.profile.service.RoleAssignmentService;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleSyncSchedulerTest {

    @Mock
    private JDA jda;

    @Mock
    private TOTStatsApiClient statsApiClient;

    @Mock
    private RoleAssignmentService roleAssignmentService;

    @InjectMocks
    private RoleSyncScheduler roleSyncScheduler;

    private static final String GUILD_ID = "guild-1";

    @Test
    void syncAllGuilds_noGuilds_doesNothing() {
        when(jda.getGuilds()).thenReturn(List.of());

        roleSyncScheduler.syncAllGuilds();

        verify(statsApiClient, never()).getBulkProfiles(anyList());
    }

    @Test
    void syncGuildMembers_apiFails_skipsAll() {
        var guild = mockGuild();
        var member = mockMember("user-1", false);

        when(statsApiClient.getBulkProfiles(List.of("user-1"))).thenReturn(Optional.empty());

        roleSyncScheduler.syncGuildMembers(guild, List.of(member));

        verify(roleAssignmentService, never()).assignRolesFromProfile(any(), any(), any());
    }

    @Test
    void syncGuildMembers_memberNotInApiResponse_skipped() {
        var guild = mockGuild();
        var member = mockMember("user-1", false);

        var response = new DiscordBulkProfileResponse();
        response.setProfiles(List.of());
        when(statsApiClient.getBulkProfiles(List.of("user-1"))).thenReturn(Optional.of(response));

        roleSyncScheduler.syncGuildMembers(guild, List.of(member));

        verify(roleAssignmentService, never()).assignRolesFromProfile(any(), any(), any());
    }

    @Test
    void syncGuildMembers_verifiedMember_assignsRolesWithCorrectProfile() {
        var guild = mockGuild();
        var member = mockMember("user-1", false);

        var entry = bulkProfileEntry("user-1", 10, 50);
        var response = new DiscordBulkProfileResponse();
        response.setProfiles(List.of(entry));
        when(statsApiClient.getBulkProfiles(List.of("user-1"))).thenReturn(Optional.of(response));
        when(roleAssignmentService.assignRolesFromProfile(any(), any(), any()))
                .thenReturn(RoleAssignmentResult.of(List.of("Prestige"), List.of()));

        roleSyncScheduler.syncGuildMembers(guild, List.of(member));

        var profileCaptor = ArgumentCaptor.forClass(DiscordProfileResponse.class);
        verify(roleAssignmentService).assignRolesFromProfile(eq(guild), eq(member), profileCaptor.capture());

        var profile = profileCaptor.getValue();
        assertThat(profile.getPrestigeLevel()).isEqualTo(10);
        assertThat(profile.getLevel()).isEqualTo(50);
    }

    @Test
    void syncGuildMembers_botMembers_excludedFromApiCall() {
        var guild = mockGuild();
        var bot = mockMember("bot-1", true);
        var human = mockMember("user-1", false);

        var response = new DiscordBulkProfileResponse();
        response.setProfiles(List.of());
        when(statsApiClient.getBulkProfiles(List.of("user-1"))).thenReturn(Optional.of(response));

        roleSyncScheduler.syncGuildMembers(guild, List.of(bot, human));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(statsApiClient).getBulkProfiles(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly("user-1");
    }

    @Test
    void syncGuildMembers_moreThan100Members_batchesRequests() {
        var guild = mockGuild();

        List<Member> members = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            members.add(mockMember("user-" + i, false));
        }

        var emptyResponse = new DiscordBulkProfileResponse();
        emptyResponse.setProfiles(List.of());
        when(statsApiClient.getBulkProfiles(anyList())).thenReturn(Optional.of(emptyResponse));

        roleSyncScheduler.syncGuildMembers(guild, members);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(statsApiClient, times(2)).getBulkProfiles(idsCaptor.capture());

        var batches = idsCaptor.getAllValues();
        assertThat(batches.get(0)).hasSize(100);
        assertThat(batches.get(1)).hasSize(50);
    }

    private Guild mockGuild() {
        var guild = mock(Guild.class);
        lenient().when(guild.getId()).thenReturn(GUILD_ID);
        return guild;
    }

    private Member mockMember(String id, boolean isBot) {
        var member = mock(Member.class);
        var user = mock(User.class);
        lenient().when(member.getId()).thenReturn(id);
        when(member.getUser()).thenReturn(user);
        when(user.isBot()).thenReturn(isBot);
        return member;
    }

    private DiscordBulkProfileEntry bulkProfileEntry(String discordUserId, int prestige, int level) {
        var entry = new DiscordBulkProfileEntry();
        entry.setDiscordUserId(discordUserId);
        entry.setProfileId(UUID.randomUUID());
        entry.setPrestigeLevel(prestige);
        entry.setLevel(level);
        return entry;
    }
}
