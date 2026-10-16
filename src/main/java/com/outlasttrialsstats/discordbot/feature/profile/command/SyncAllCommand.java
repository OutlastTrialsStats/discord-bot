package com.outlasttrialsstats.discordbot.feature.profile.command;

import com.outlasttrialsstats.discordbot.feature.profile.service.GuildMemberLoader;
import com.outlasttrialsstats.discordbot.feature.profile.service.GuildSyncService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SyncAllCommand {

    private final GuildSyncService guildSyncService;
    private final GuildMemberLoader memberLoader;
    private final MessageService messageService;
    @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    private final TaskExecutor taskExecutor;

    public void onSyncAll(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);

        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            event.reply(messageService.getMessage(guildId, "error.missing_permission.manage_roles"))
                    .setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        hook.editOriginal(messageService.getMessage(guildId, "sync.starting")).queue();

        try {
            taskExecutor.execute(() -> syncAndReport(guild, hook));
        } catch (RuntimeException e) {
            replyFailed(guild, hook, e);
        }
    }

    private void syncAndReport(Guild guild, InteractionHook hook) {
        try {
            var result = guildSyncService.syncMembers(guild, memberLoader.load(guild));
            hook.editOriginal(messageService.getMessage(guild.getId(), "sync.completed",
                    result.updated(), result.unchanged(), result.unverified(), result.failed())).queue();
        } catch (Exception e) {
            replyFailed(guild, hook, e);
        }
    }

    private void replyFailed(Guild guild, InteractionHook hook, Exception e) {
        log.warn("Failed to sync members for guild {}", guild.getId(), e);
        hook.editOriginal(messageService.getMessage(guild.getId(), "sync.failed")).queue();
    }
}
