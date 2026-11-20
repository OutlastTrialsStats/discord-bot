package com.outlasttrialsstats.discordbot.feature.profile.command;

import com.outlasttrialsstats.discordbot.feature.profile.service.GuildMemberLoader;
import com.outlasttrialsstats.discordbot.feature.profile.service.GuildSyncService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SyncAllCommand {

    static final Duration COOLDOWN = Duration.ofMinutes(10);

    private final GuildSyncService guildSyncService;
    private final GuildMemberLoader memberLoader;
    private final MessageService messageService;
    private final TaskExecutor taskExecutor;
    private final Clock clock;

    private final Map<String, Instant> lastRunByGuild = new ConcurrentHashMap<>();

    @Autowired
    public SyncAllCommand(GuildSyncService guildSyncService, GuildMemberLoader memberLoader,
                          MessageService messageService,
                          @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
                          TaskExecutor taskExecutor) {
        this(guildSyncService, memberLoader, messageService, taskExecutor, Clock.systemUTC());
    }

    SyncAllCommand(GuildSyncService guildSyncService, GuildMemberLoader memberLoader,
                   MessageService messageService, TaskExecutor taskExecutor, Clock clock) {
        this.guildSyncService = guildSyncService;
        this.memberLoader = memberLoader;
        this.messageService = messageService;
        this.taskExecutor = taskExecutor;
        this.clock = clock;
    }

    public void onSyncAll(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);

        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            replyEphemeral(event, messageService.getMessage(guildId, "error.missing_permission.manage_roles"));
            return;
        }

        if (!guildSyncService.tryStartGuildSync(guildId)) {
            replyEphemeral(event, messageService.getMessage(guildId, "sync.in_progress"));
            return;
        }

        Instant now = clock.instant();
        Instant lastRun = lastRunByGuild.get(guildId);
        if (lastRun != null && now.isBefore(lastRun.plus(COOLDOWN))) {
            guildSyncService.finishGuildSync(guildId);
            String availableAt = String.valueOf(lastRun.plus(COOLDOWN).getEpochSecond());
            replyEphemeral(event, messageService.getMessage(guildId, "sync.cooldown", availableAt));
            return;
        }
        lastRunByGuild.put(guildId, now);

        InteractionHook hook;
        try {
            event.deferReply(true).queue();
            hook = event.getHook();
            hook.editOriginal(messageService.getMessage(guildId, "sync.starting")).queue();
        } catch (RuntimeException e) {
            release(guildId, now);
            throw e;
        }

        try {
            taskExecutor.execute(() -> syncAndReport(guild, hook, now));
        } catch (RuntimeException e) {
            release(guildId, now);
            replyFailed(guild, hook, e);
        }
    }

    private void syncAndReport(Guild guild, InteractionHook hook, Instant startedAt) {
        try {
            var result = guildSyncService.syncMembers(guild, memberLoader.load(guild));
            hook.editOriginal(messageService.getMessage(guild.getId(), "sync.completed",
                    result.updated(), result.unchanged(), result.unverified(), result.failed())).queue();
        } catch (Exception e) {
            lastRunByGuild.remove(guild.getId(), startedAt);
            replyFailed(guild, hook, e);
        } finally {
            guildSyncService.finishGuildSync(guild.getId());
        }
    }

    private void release(String guildId, Instant startedAt) {
        guildSyncService.finishGuildSync(guildId);
        lastRunByGuild.remove(guildId, startedAt);
    }

    private void replyEphemeral(SlashCommandInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    private void replyFailed(Guild guild, InteractionHook hook, Exception e) {
        log.warn("Failed to sync members for guild {}", guild.getId(), e);
        hook.editOriginal(messageService.getMessage(guild.getId(), "sync.failed")).queue();
    }
}
