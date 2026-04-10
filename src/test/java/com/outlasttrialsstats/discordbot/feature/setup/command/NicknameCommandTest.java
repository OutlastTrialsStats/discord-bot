package com.outlasttrialsstats.discordbot.feature.setup.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NicknameCommandTest {

    @Mock
    private GuildServerRepository guildServerRepository;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private NicknameCommand nicknameCommand;

    private static final String GUILD_ID = "guild-1";

    @Test
    void onNickname_enable_savesAndReplies() {
        var event = mockEvent(true);
        var server = new GuildServer(GUILD_ID);
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.of(server));
        when(messageService.getMessage(GUILD_ID, "setup.nickname.enabled")).thenReturn("Enabled!");

        var replyAction = mock(ReplyCallbackAction.class);
        when(event.reply("Enabled!")).thenReturn(replyAction);
        when(replyAction.setEphemeral(true)).thenReturn(replyAction);

        nicknameCommand.onNickname(event);

        var captor = ArgumentCaptor.forClass(GuildServer.class);
        verify(guildServerRepository).save(captor.capture());
        assertThat(captor.getValue().isAutoNickname()).isTrue();
    }

    @Test
    void onNickname_disable_savesAndReplies() {
        var event = mockEvent(false);
        var server = new GuildServer(GUILD_ID);
        server.setAutoNickname(true);
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.of(server));
        when(messageService.getMessage(GUILD_ID, "setup.nickname.disabled")).thenReturn("Disabled!");

        var replyAction = mock(ReplyCallbackAction.class);
        when(event.reply("Disabled!")).thenReturn(replyAction);
        when(replyAction.setEphemeral(true)).thenReturn(replyAction);

        nicknameCommand.onNickname(event);

        var captor = ArgumentCaptor.forClass(GuildServer.class);
        verify(guildServerRepository).save(captor.capture());
        assertThat(captor.getValue().isAutoNickname()).isFalse();
    }

    @Test
    void onNickname_noExistingServer_createsNew() {
        var event = mockEvent(true);
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.empty());
        when(messageService.getMessage(GUILD_ID, "setup.nickname.enabled")).thenReturn("Enabled!");

        var replyAction = mock(ReplyCallbackAction.class);
        when(event.reply("Enabled!")).thenReturn(replyAction);
        when(replyAction.setEphemeral(true)).thenReturn(replyAction);

        nicknameCommand.onNickname(event);

        var captor = ArgumentCaptor.forClass(GuildServer.class);
        verify(guildServerRepository).save(captor.capture());
        assertThat(captor.getValue().getGuildId()).isEqualTo(GUILD_ID);
        assertThat(captor.getValue().isAutoNickname()).isTrue();
    }

    @Test
    void onNickname_enable_missingPermission_repliesError() {
        var event = mockEvent(true, false);
        when(messageService.getMessage(GUILD_ID, "error.missing_permission.manage_nicknames"))
                .thenReturn("No permission!");

        var replyAction = mock(ReplyCallbackAction.class);
        when(event.reply("No permission!")).thenReturn(replyAction);
        when(replyAction.setEphemeral(true)).thenReturn(replyAction);

        nicknameCommand.onNickname(event);

        verify(guildServerRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private SlashCommandInteractionEvent mockEvent(boolean enabled) {
        return mockEvent(enabled, true);
    }

    private SlashCommandInteractionEvent mockEvent(boolean enabled, boolean hasNicknamePermission) {
        var event = mock(SlashCommandInteractionEvent.class);
        var guild = mock(Guild.class);
        when(guild.getId()).thenReturn(GUILD_ID);
        when(event.getGuild()).thenReturn(guild);

        var jda = mock(JDA.class);
        when(event.getJDA()).thenReturn(jda);
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);

        var selfMember = mock(SelfMember.class);
        lenient().when(guild.getSelfMember()).thenReturn(selfMember);
        lenient().when(selfMember.hasPermission(Permission.NICKNAME_MANAGE)).thenReturn(hasNicknamePermission);

        var option = mock(OptionMapping.class);
        when(option.getAsBoolean()).thenReturn(enabled);
        when(event.getOption("enabled")).thenReturn(option);

        return event;
    }
}
