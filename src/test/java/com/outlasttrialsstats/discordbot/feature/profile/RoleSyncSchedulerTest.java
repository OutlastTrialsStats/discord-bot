package com.outlasttrialsstats.discordbot.feature.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.feature.profile.dto.GuildSyncResult;
import com.outlasttrialsstats.discordbot.feature.profile.service.GuildMemberLoader;
import com.outlasttrialsstats.discordbot.feature.profile.service.GuildSyncService;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleSyncSchedulerTest {

    @Mock
    private JDA jda;

    @Mock
    private GuildMemberLoader memberLoader;

    @Mock
    private GuildSyncService guildSyncService;

    @InjectMocks
    private RoleSyncScheduler roleSyncScheduler;

    @Test
    void syncAllGuilds_noGuilds_doesNothing() {
        when(jda.getGuilds()).thenReturn(List.of());

        roleSyncScheduler.syncAllGuilds();

        verify(guildSyncService, never()).syncMembers(any(), any());
    }

    @Test
    void syncAllGuilds_processesGuildsSequentiallyUnderGuard() {
        var guild1 = mockGuild("guild-1");
        var guild2 = mockGuild("guild-2");
        List<Member> members1 = List.of(mock(Member.class));
        List<Member> members2 = List.of(mock(Member.class));
        when(memberLoader.load(guild1)).thenReturn(members1);
        when(memberLoader.load(guild2)).thenReturn(members2);
        when(jda.getGuilds()).thenReturn(List.of(guild1, guild2));
        when(guildSyncService.tryStartGuildSync(any())).thenReturn(true);
        when(guildSyncService.syncMembers(any(), any())).thenReturn(new GuildSyncResult(0, 0, 0, 0));

        roleSyncScheduler.syncAllGuilds();

        InOrder order = inOrder(memberLoader, guildSyncService);
        order.verify(guildSyncService).tryStartGuildSync("guild-1");
        order.verify(memberLoader).load(guild1);
        order.verify(guildSyncService).syncMembers(guild1, members1);
        order.verify(guildSyncService).finishGuildSync("guild-1");
        order.verify(guildSyncService).tryStartGuildSync("guild-2");
        order.verify(memberLoader).load(guild2);
        order.verify(guildSyncService).syncMembers(guild2, members2);
        order.verify(guildSyncService).finishGuildSync("guild-2");
    }

    @Test
    void syncAllGuilds_failingGuild_releasesGuardAndContinues() {
        var failingGuild = mockGuild("guild-1");
        var healthyGuild = mockGuild("guild-2");
        List<Member> members = List.of(mock(Member.class));
        when(memberLoader.load(failingGuild)).thenThrow(new IllegalStateException("boom"));
        when(memberLoader.load(healthyGuild)).thenReturn(members);
        when(jda.getGuilds()).thenReturn(List.of(failingGuild, healthyGuild));
        when(guildSyncService.tryStartGuildSync(any())).thenReturn(true);

        roleSyncScheduler.syncAllGuilds();

        verify(guildSyncService).finishGuildSync("guild-1");
        verify(guildSyncService).syncMembers(healthyGuild, members);
    }

    @Test
    void syncAllGuilds_syncAlreadyInProgress_skipsGuild() {
        var guild = mockGuild("guild-1");
        when(jda.getGuilds()).thenReturn(List.of(guild));
        when(guildSyncService.tryStartGuildSync("guild-1")).thenReturn(false);

        roleSyncScheduler.syncAllGuilds();

        verify(memberLoader, never()).load(any());
        verify(guildSyncService, never()).syncMembers(any(), any());
        verify(guildSyncService, never()).finishGuildSync(any());
    }

    private Guild mockGuild(String id) {
        var guild = mock(Guild.class);
        lenient().when(guild.getId()).thenReturn(id);
        return guild;
    }
}
