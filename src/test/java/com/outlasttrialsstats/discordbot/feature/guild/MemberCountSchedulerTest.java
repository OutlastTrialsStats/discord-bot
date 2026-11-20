package com.outlasttrialsstats.discordbot.feature.guild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MemberCountSchedulerTest {

    private final JDA jda = mock(JDA.class);
    private final GuildServerRepository repository = mock(GuildServerRepository.class);
    private final PublicInviteResolver inviteResolver = mock(PublicInviteResolver.class);
    private final MemberCountScheduler scheduler = new MemberCountScheduler(jda, repository, inviteResolver);

    @Test
    void storesMemberCountAndPublicInvite() {
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn("1");
        when(guild.getName()).thenReturn("Server");
        when(guild.getMemberCount()).thenReturn(42);
        when(jda.getGuilds()).thenReturn(List.of(guild));
        GuildServer server = new GuildServer("1");
        server.setInviteUrl("https://discord.gg/old");
        when(repository.findById("1")).thenReturn(Optional.of(server));
        when(inviteResolver.resolve(guild, "https://discord.gg/old")).thenReturn("https://discord.gg/new");
        when(repository.findGuildIdsWithBotPresent()).thenReturn(List.of());

        scheduler.updateMemberCounts();

        ArgumentCaptor<GuildServer> saved = ArgumentCaptor.forClass(GuildServer.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getMemberCount()).isEqualTo(42);
        assertThat(saved.getValue().getInviteUrl()).isEqualTo("https://discord.gg/new");
        assertThat(saved.getValue().isBotPresent()).isTrue();
    }

    @Test
    void marksGuildsTheBotLeft_butNotTemporarilyUnavailableOnes() {
        when(jda.getGuilds()).thenReturn(List.of());
        when(repository.findGuildIdsWithBotPresent()).thenReturn(List.of("10", "20"));
        when(jda.getGuildById("10")).thenReturn(null);
        when(jda.isUnavailable(10L)).thenReturn(false);
        when(jda.getGuildById("20")).thenReturn(null);
        when(jda.isUnavailable(20L)).thenReturn(true);

        scheduler.updateMemberCounts();

        verify(repository).markBotAbsent("10");
        verify(repository, never()).markBotAbsent("20");
    }
}
