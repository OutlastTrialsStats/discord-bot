package com.outlasttrialsstats.discordbot.feature.widget.command;

import com.outlasttrialsstats.discordbot.entity.WidgetEnrollment;
import com.outlasttrialsstats.discordbot.entity.WidgetStatus;
import com.outlasttrialsstats.discordbot.feature.widget.config.WidgetProperties;
import com.outlasttrialsstats.discordbot.feature.widget.dto.WidgetPushResult;
import com.outlasttrialsstats.discordbot.feature.widget.service.WidgetEnrollmentService;
import com.outlasttrialsstats.discordbot.feature.widget.service.WidgetEnrollmentService.BeginResult;
import com.outlasttrialsstats.discordbot.feature.widget.service.WidgetPushService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WidgetCommand {

    private final WidgetEnrollmentService enrollmentService;
    private final WidgetPushService pushService;
    private final WidgetProperties widgetProperties;
    private final MessageService messageService;

    public void onWidget(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        if (!widgetProperties.enabled()) {
            event.reply(messageService.getMessage(guildId, "widget.unavailable"))
                    .setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        switch (subcommand) {
            case "enable" -> onEnable(event, guildId);
            case "disable" -> onDisable(event, guildId);
            case "status" -> onStatus(event, guildId);
            case "refresh" -> onRefresh(event, guildId);
            default -> {}
        }
    }

    private void onEnable(SlashCommandInteractionEvent event, String guildId) {
        event.deferReply(true).queue();
        BeginResult result = enrollmentService.beginEnrollment(event.getUser().getId());

        switch (result) {
            case BeginResult.NotVerified _ -> event.getHook()
                    .editOriginal(messageService.getMessage(guildId, "widget.not_verified")).queue();
            case BeginResult.AlreadyActive _ -> event.getHook()
                    .editOriginal(messageService.getMessage(guildId, "widget.enable.already_active")).queue();
            case BeginResult.Ready(String authorizeUrl) -> event.getHook()
                    .editOriginal(messageService.getMessage(guildId, "widget.enable.prompt"))
                    .setComponents(ActionRow.of(Button.link(authorizeUrl,
                            messageService.getMessage(guildId, "widget.enable.button"))))
                    .queue();
        }
    }

    private void onDisable(SlashCommandInteractionEvent event, String guildId) {
        event.deferReply(true).queue();
        boolean existed = enrollmentService.disable(event.getUser().getId());
        String key = existed ? "widget.disabled" : "widget.status.none";
        event.getHook().editOriginal(messageService.getMessage(guildId, key)).queue();
    }

    private void onStatus(SlashCommandInteractionEvent event, String guildId) {
        var enrollment = enrollmentService.getEnrollment(event.getUser().getId());
        if (enrollment.isEmpty()) {
            event.reply(messageService.getMessage(guildId, "widget.status.none"))
                    .setEphemeral(true).queue();
            return;
        }
        event.reply(statusMessage(guildId, enrollment.get())).setEphemeral(true).queue();
    }

    private String statusMessage(String guildId, WidgetEnrollment enrollment) {
        return switch (enrollment.getStatus()) {
            case ACTIVE -> messageService.getMessage(guildId, "widget.status.active",
                    enrollment.getLastPushedAt() != null
                            ? "<t:" + enrollment.getLastPushedAt().getEpochSecond() + ":R>"
                            : "-");
            case PENDING -> messageService.getMessage(guildId, "widget.status.pending");
            case REVOKED -> messageService.getMessage(guildId, "widget.status.revoked");
            case DISABLED -> messageService.getMessage(guildId, "widget.status.disabled");
            case ERROR -> messageService.getMessage(guildId, "widget.status.error",
                    enrollment.getLastError() != null ? enrollment.getLastError() : "-");
        };
    }

    private void onRefresh(SlashCommandInteractionEvent event, String guildId) {
        event.deferReply(true).queue();
        String userId = event.getUser().getId();

        var enrollment = enrollmentService.getEnrollment(userId);
        if (enrollment.isEmpty() || enrollment.get().getStatus() != WidgetStatus.ACTIVE) {
            event.getHook()
                    .editOriginal(messageService.getMessage(guildId, "widget.refresh.not_active")).queue();
            return;
        }

        String key = switch (pushService.pushOne(enrollment.get())) {
            case WidgetPushResult.Success _ -> "widget.refreshed";
            case WidgetPushResult.Revoked _ -> "widget.status.revoked";
            case WidgetPushResult.RateLimited _, WidgetPushResult.Failed _ -> "widget.refresh_failed";
        };
        event.getHook().editOriginal(messageService.getMessage(guildId, key)).queue();
    }
}
