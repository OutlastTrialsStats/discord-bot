package com.outlasttrialsstats.discordbot.feature.profile.command;

import com.outlasttrialsstats.discordbot.feature.profile.service.GuildMemberLoader;
import com.outlasttrialsstats.discordbot.feature.profile.service.GuildSyncService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
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
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();
        start(event.getGuild(), message -> hook.editOriginal(message).queue());
    }

    public void start(Guild guild, Consumer<String> reply) {
        String guildId = guild.getId();

        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            reply.accept(messageService.getMessage(guildId, "error.missing_permission.manage_roles"));
            return;
        }

        if (!guildSyncService.tryStartGuildSync(guildId)) {
            reply.accept(messageService.getMessage(guildId, "sync.in_progress"));
            return;
        }

        Instant now = clock.instant();
        Instant lastRun = lastRunByGuild.get(guildId);
        if (lastRun != null && now.isBefore(lastRun.plus(COOLDOWN))) {
            guildSyncService.finishGuildSync(guildId);
            String availableAt = String.valueOf(lastRun.plus(COOLDOWN).getEpochSecond());
            reply.accept(messageService.getMessage(guildId, "sync.cooldown", availableAt));
            return;
        }
        lastRunByGuild.put(guildId, now);

        try {
            reply.accept(messageService.getMessage(guildId, "sync.starting"));
            taskExecutor.execute(() -> syncAndReport(guild, reply, now));
        } catch (RuntimeException e) {
            release(guildId, now);
            replyFailed(guild, reply, e);
        }
    }

    private void syncAndReport(Guild guild, Consumer<String> reply, Instant startedAt) {
        try {
            var result = guildSyncService.syncMembers(guild, memberLoader.load(guild));
            reply.accept(messageService.getMessage(guild.getId(), "sync.completed",
                    result.updated(), result.unchanged(), result.unverified(), result.failed()));
        } catch (Exception e) {
            lastRunByGuild.remove(guild.getId(), startedAt);
            replyFailed(guild, reply, e);
        } finally {
            guildSyncService.finishGuildSync(guild.getId());
        }
    }

    private void release(String guildId, Instant startedAt) {
        guildSyncService.finishGuildSync(guildId);
        lastRunByGuild.remove(guildId, startedAt);
    }

    private void replyFailed(Guild guild, Consumer<String> reply, Exception e) {
        log.warn("Failed to sync members for guild {}", guild.getId(), e);
        reply.accept(messageService.getMessage(guild.getId(), "sync.failed"));
    }
}
