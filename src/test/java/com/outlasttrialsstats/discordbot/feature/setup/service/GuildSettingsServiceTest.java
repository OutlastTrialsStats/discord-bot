package com.outlasttrialsstats.discordbot.feature.setup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.Optional;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfMember;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuildSettingsServiceTest {

    private static final String GUILD_ID = "guild-1";

    @Mock
    private GuildServerRepository guildServerRepository;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private GuildSettingsService settingsService;

    @Test
    void setAutoNickname_enable_saves() {
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.of(new GuildServer(GUILD_ID)));
        when(messageService.getMessage(GUILD_ID, "setup.nickname.enabled")).thenReturn("Enabled!");

        assertThat(settingsService.setAutoNickname(guild(true), true)).isEqualTo("Enabled!");

        assertThat(savedServer().isAutoNickname()).isTrue();
    }

    @Test
    void setAutoNickname_disable_saves() {
        GuildServer server = new GuildServer(GUILD_ID);
        server.setAutoNickname(true);
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.of(server));
        when(messageService.getMessage(GUILD_ID, "setup.nickname.disabled")).thenReturn("Disabled!");

        assertThat(settingsService.setAutoNickname(guild(false), false)).isEqualTo("Disabled!");

        assertThat(savedServer().isAutoNickname()).isFalse();
    }

    @Test
    void setAutoNickname_noExistingServer_createsNew() {
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.empty());
        when(messageService.getMessage(GUILD_ID, "setup.nickname.enabled")).thenReturn("Enabled!");

        settingsService.setAutoNickname(guild(true), true);

        GuildServer saved = savedServer();
        assertThat(saved.getGuildId()).isEqualTo(GUILD_ID);
        assertThat(saved.isAutoNickname()).isTrue();
    }

    @Test
    void setAutoNickname_enableWithoutPermission_repliesErrorWithoutSaving() {
        when(messageService.getMessage(GUILD_ID, "error.missing_permission.manage_nicknames"))
                .thenReturn("No permission!");

        assertThat(settingsService.setAutoNickname(guild(false), true)).isEqualTo("No permission!");

        verify(guildServerRepository, never()).save(any());
    }

    @Test
    void setUserCommandsVisible_savesFlag() {
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.of(new GuildServer(GUILD_ID)));
        when(messageService.getMessage(GUILD_ID, "setup.toggle_user_command_visibility.enabled")).thenReturn("public");

        assertThat(settingsService.setUserCommandsVisible(GUILD_ID, true)).isEqualTo("public");

        assertThat(savedServer().isUserCommandsVisible()).isTrue();
    }

    @Test
    void setLanguage_storesLanguageAndRepliesWithName() {
        when(messageService.getMessage(GUILD_ID, "setup.language.success", "Deutsch")).thenReturn("ok");

        assertThat(settingsService.setLanguage(GUILD_ID, "de")).isEqualTo("ok");

        verify(messageService).setLanguage(GUILD_ID, "de");
    }

    private Guild guild(boolean hasNicknamePermission) {
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn(GUILD_ID);
        SelfMember selfMember = mock(SelfMember.class);
        lenient().when(guild.getSelfMember()).thenReturn(selfMember);
        lenient().when(selfMember.hasPermission(Permission.NICKNAME_MANAGE)).thenReturn(hasNicknamePermission);
        return guild;
    }

    private GuildServer savedServer() {
        ArgumentCaptor<GuildServer> captor = ArgumentCaptor.forClass(GuildServer.class);
        verify(guildServerRepository).save(captor.capture());
        return captor.getValue();
    }
}
