package com.outlasttrialsstats.discordbot.feature.profile.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.feature.profile.dto.GuildSyncResult;
import com.outlasttrialsstats.discordbot.feature.profile.service.GuildMemberLoader;
import com.outlasttrialsstats.discordbot.feature.profile.service.GuildSyncService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

@ExtendWith(MockitoExtension.class)
class SyncAllCommandTest {

    private static final String GUILD_ID = "guild-1";

    @Mock
    private GuildSyncService guildSyncService;

    @Mock
    private GuildMemberLoader memberLoader;

    @Mock
    private MessageService messageService;

    @Mock
    private SlashCommandInteractionEvent event;

    @Mock
    private Guild guild;

    @Mock
    private InteractionHook hook;

    private final List<Runnable> submitted = new ArrayList<>();

    private SyncAllCommand command;

    @BeforeEach
    void setUp() {
        command = new SyncAllCommand(guildSyncService, memberLoader, messageService, submitted::add);

        var jda = mock(JDA.class);
        var selfMember = mock(SelfMember.class);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn(GUILD_ID);
        when(event.getJDA()).thenReturn(jda);
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
        when(guild.getSelfMember()).thenReturn(selfMember);
        when(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        when(event.deferReply(true)).thenReturn(mock(ReplyCallbackAction.class));
        when(event.getHook()).thenReturn(hook);
        when(hook.editOriginal(anyString())).thenReturn(mock(WebhookMessageEditAction.class));
        lenient().when(messageService.getMessage(eq(GUILD_ID), anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(1));
    }

    @Test
    void onSyncAll_runsLoadAndSyncOnExecutor() {
        var members = List.of(mock(Member.class));
        when(memberLoader.load(guild)).thenReturn(members);
        when(guildSyncService.syncMembers(guild, members)).thenReturn(new GuildSyncResult(1, 2, 3, 0));

        command.onSyncAll(event);

        verify(memberLoader, never()).load(any());
        assertThat(submitted).hasSize(1);

        submitted.getFirst().run();
        verify(messageService).getMessage(GUILD_ID, "sync.completed", 1, 2, 3, 0);
        verify(hook).editOriginal("sync.completed");
    }

    @Test
    void onSyncAll_loadFails_editsReplyWithError() {
        when(memberLoader.load(guild)).thenThrow(new IllegalStateException("timeout"));

        command.onSyncAll(event);
        submitted.getFirst().run();

        verify(hook).editOriginal("sync.failed");
        verify(guildSyncService, never()).syncMembers(any(), any());
    }

    @Test
    void onSyncAll_syncThrows_editsReplyWithError() {
        var members = List.of(mock(Member.class));
        when(memberLoader.load(guild)).thenReturn(members);
        when(guildSyncService.syncMembers(guild, members)).thenThrow(new IllegalStateException("db down"));

        command.onSyncAll(event);
        submitted.getFirst().run();

        verify(hook).editOriginal("sync.failed");
    }

    @Test
    void onSyncAll_executorRejects_editsReplyWithError() {
        command = new SyncAllCommand(guildSyncService, memberLoader, messageService, r -> {
            throw new TaskRejectedException("shutting down");
        });

        command.onSyncAll(event);

        verify(hook).editOriginal("sync.failed");
        verify(memberLoader, never()).load(any());
    }
}
