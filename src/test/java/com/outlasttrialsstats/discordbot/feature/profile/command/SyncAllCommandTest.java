package com.outlasttrialsstats.discordbot.feature.profile.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.feature.profile.dto.GuildSyncResult;
import com.outlasttrialsstats.discordbot.feature.profile.service.GuildMemberLoader;
import com.outlasttrialsstats.discordbot.feature.profile.service.GuildSyncService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

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

    @Mock
    private Clock clock;

    private final List<Runnable> submitted = new ArrayList<>();

    private ReplyCallbackAction replyAction;

    private SyncAllCommand command;

    @BeforeEach
    void setUp() {
        command = new SyncAllCommand(guildSyncService, memberLoader, messageService, submitted::add, clock);

        var selfMember = mock(SelfMember.class);
        replyAction = mock(ReplyCallbackAction.class);
        lenient().when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn(GUILD_ID);
        when(guild.getSelfMember()).thenReturn(selfMember);
        when(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        lenient().when(clock.instant()).thenReturn(NOW);
        lenient().when(guildSyncService.tryStartGuildSync(GUILD_ID)).thenReturn(true);
        lenient().when(event.deferReply(true)).thenReturn(mock(ReplyCallbackAction.class));
        lenient().when(event.getHook()).thenReturn(hook);
        lenient().when(event.reply(anyString())).thenReturn(replyAction);
        lenient().when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        lenient().when(hook.editOriginal(anyString())).thenReturn(mock(WebhookMessageEditAction.class));
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
        verify(guildSyncService, never()).finishGuildSync(any());
        assertThat(submitted).hasSize(1);

        submitted.getFirst().run();
        verify(messageService).getMessage(GUILD_ID, "sync.completed", 1, 2, 3, 0);
        verify(hook).editOriginal("sync.completed");
        verify(guildSyncService).finishGuildSync(GUILD_ID);
    }

    @Test
    void onSyncAll_syncAlreadyInProgress_repliesBusy() {
        when(guildSyncService.tryStartGuildSync(GUILD_ID)).thenReturn(false);

        command.onSyncAll(event);

        verify(hook).editOriginal("sync.in_progress");
        verify(guildSyncService, never()).finishGuildSync(any());
        assertThat(submitted).isEmpty();
    }

    @Test
    void onSyncAll_withinCooldown_repliesCooldownAndReleasesGuard() {
        runSuccessfulSync();

        when(clock.instant()).thenReturn(NOW.plus(SyncAllCommand.COOLDOWN).minusSeconds(1));
        command.onSyncAll(event);

        String availableAt = String.valueOf(NOW.plus(SyncAllCommand.COOLDOWN).getEpochSecond());
        verify(messageService).getMessage(GUILD_ID, "sync.cooldown", availableAt);
        verify(hook).editOriginal("sync.cooldown");
        verify(guildSyncService, times(2)).finishGuildSync(GUILD_ID);
        assertThat(submitted).hasSize(1);
    }

    @Test
    void onSyncAll_afterCooldown_runsAgain() {
        runSuccessfulSync();

        when(clock.instant()).thenReturn(NOW.plus(SyncAllCommand.COOLDOWN));
        command.onSyncAll(event);

        assertThat(submitted).hasSize(2);
    }

    @Test
    void onSyncAll_syncFailed_liftsCooldown() {
        when(memberLoader.load(guild)).thenThrow(new IllegalStateException("timeout"));
        command.onSyncAll(event);
        submitted.getFirst().run();

        command.onSyncAll(event);

        assertThat(submitted).hasSize(2);
        verify(hook, never()).editOriginal("sync.cooldown");
    }

    @Test
    void onSyncAll_loadFails_editsReplyWithError() {
        when(memberLoader.load(guild)).thenThrow(new IllegalStateException("timeout"));

        command.onSyncAll(event);
        submitted.getFirst().run();

        verify(hook).editOriginal("sync.failed");
        verify(guildSyncService, never()).syncMembers(any(), any());
        verify(guildSyncService).finishGuildSync(GUILD_ID);
    }

    @Test
    void onSyncAll_syncThrows_editsReplyWithError() {
        var members = List.of(mock(Member.class));
        when(memberLoader.load(guild)).thenReturn(members);
        when(guildSyncService.syncMembers(guild, members)).thenThrow(new IllegalStateException("db down"));

        command.onSyncAll(event);
        submitted.getFirst().run();

        verify(hook).editOriginal("sync.failed");
        verify(guildSyncService).finishGuildSync(GUILD_ID);
    }

    @Test
    void onSyncAll_executorRejects_editsReplyWithErrorAndReleasesGuard() {
        command = new SyncAllCommand(guildSyncService, memberLoader, messageService, r -> {
            throw new TaskRejectedException("shutting down");
        }, clock);

        command.onSyncAll(event);

        verify(hook).editOriginal("sync.failed");
        verify(memberLoader, never()).load(any());
        verify(guildSyncService).finishGuildSync(GUILD_ID);
    }

    @Test
    void start_reportsStartAndResultThroughCallback() {
        var members = List.of(mock(Member.class));
        when(memberLoader.load(guild)).thenReturn(members);
        when(guildSyncService.syncMembers(guild, members)).thenReturn(new GuildSyncResult(1, 0, 0, 0));
        List<String> replies = new ArrayList<>();

        command.start(guild, replies::add);
        submitted.getFirst().run();

        assertThat(replies).containsExactly("sync.starting", "sync.completed");
    }

    private void runSuccessfulSync() {
        var members = List.of(mock(Member.class));
        when(memberLoader.load(guild)).thenReturn(members);
        when(guildSyncService.syncMembers(guild, members)).thenReturn(new GuildSyncResult(0, 0, 0, 0));
        command.onSyncAll(event);
        submitted.getFirst().run();
    }
}
