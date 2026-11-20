package com.outlasttrialsstats.discordbot.feature.guild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.unions.DefaultGuildChannelUnion;
import net.dv8tion.jda.api.requests.restaction.InviteAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublicInviteResolverTest {

    private final PublicInviteResolver resolver = new PublicInviteResolver();
    private final Guild guild = mock(Guild.class);
    private final SelfMember selfMember = mock(SelfMember.class);
    private final DefaultGuildChannelUnion channel = mock(DefaultGuildChannelUnion.class);

    @BeforeEach
    void setUp() {
        when(guild.getId()).thenReturn("guild-1");
        when(guild.getSelfMember()).thenReturn(selfMember);
        when(guild.getFeatures()).thenReturn(Set.of());
    }

    @Test
    void vanityUrl_isUsedWithoutCreatingAnInvite() {
        when(guild.getVanityUrl()).thenReturn("https://discord.gg/outlast");

        assertThat(resolver.resolve(guild, null)).isEqualTo("https://discord.gg/outlast");
        verify(guild, never()).getDefaultChannel();
    }

    @Test
    void privateServer_getsNoInvite() {
        when(guild.getFeatures()).thenReturn(Set.of("COMMUNITY"));

        assertThat(resolver.resolve(guild, "https://discord.gg/old")).isNull();
        verify(guild, never()).getDefaultChannel();
    }

    @Test
    void discoverableServer_withoutStoredInvite_createsPermanentInvite() {
        when(guild.getFeatures()).thenReturn(Set.of(PublicInviteResolver.DISCOVERABLE));
        when(guild.getDefaultChannel()).thenReturn(channel);
        when(selfMember.hasPermission(channel, Permission.CREATE_INSTANT_INVITE)).thenReturn(true);
        InviteAction action = mock(InviteAction.class, RETURNS_SELF);
        Invite invite = mock(Invite.class);
        when(invite.getUrl()).thenReturn("https://discord.gg/abc");
        when(action.complete()).thenReturn(invite);
        when(channel.createInvite()).thenReturn(action);

        assertThat(resolver.resolve(guild, null)).isEqualTo("https://discord.gg/abc");
        verify(action).setMaxAge(0);
        verify(action).setUnique(false);
    }

    @Test
    void discoverableServer_withoutInvitePermission_getsNoInvite() {
        when(guild.getFeatures()).thenReturn(Set.of(PublicInviteResolver.DISCOVERABLE));
        when(guild.getDefaultChannel()).thenReturn(channel);
        when(selfMember.hasPermission(channel, Permission.CREATE_INSTANT_INVITE)).thenReturn(false);

        assertThat(resolver.resolve(guild, null)).isNull();
        verify(channel, never()).createInvite();
    }
}
