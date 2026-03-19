package com.outlasttrialsstats.discordbot.command;

import com.outlasttrialsstats.discordbot.feature.leaderboard.command.LeaderboardCommand;
import com.outlasttrialsstats.discordbot.feature.leaderboard.command.LeaderboardSetupCommand;
import com.outlasttrialsstats.discordbot.feature.profile.command.ProfileCommand;
import com.outlasttrialsstats.discordbot.feature.profile.command.SyncAllCommand;
import com.outlasttrialsstats.discordbot.feature.setup.command.DeleteCommand;
import com.outlasttrialsstats.discordbot.feature.setup.command.LanguageCommand;
import com.outlasttrialsstats.discordbot.feature.setup.command.MessagesCommand;
import com.outlasttrialsstats.discordbot.feature.setup.command.RoleMappingCommand;
import com.outlasttrialsstats.discordbot.feature.setup.command.StartBasicCommand;
import java.awt.Color;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommandRouter extends ListenerAdapter {

    private final ProfileCommand profileCommand;
    private final SyncAllCommand syncAllCommand;
    private final DeleteCommand deleteCommand;
    private final LanguageCommand languageCommand;
    private final MessagesCommand messagesCommand;
    private final RoleMappingCommand roleMappingCommand;
    private final StartBasicCommand startBasicCommand;
    private final LeaderboardCommand leaderboardCommand;
    private final LeaderboardSetupCommand leaderboardSetupCommand;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        try {
            switch (event.getName()) {
                case "sync-profile" -> profileCommand.onProfileUpdate(event);
                case "sync-all" -> syncAllCommand.onSyncAll(event);
                case "leaderboard" -> leaderboardCommand.onLeaderboard(event);
                case "setup" -> routeSetup(event);
                default -> {}
            }
        } catch (Exception e) {
            log.error("Command execution failed: {}", event.getFullCommandName(), e);
            replyError(event);
        }
    }

    private void routeSetup(SlashCommandInteractionEvent event) {
        String subcommandGroup = event.getSubcommandGroup();
        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        if ("role-mapping".equals(subcommandGroup)) {
            roleMappingCommand.onRoleMapping(event, subcommand);
        } else if ("remove-role-mapping".equals(subcommandGroup)) {
            roleMappingCommand.onRemoveRoleMapping(event, subcommand);
        } else {
            switch (subcommand) {
                case "delete" -> deleteCommand.onDelete(event);
                case "language" -> languageCommand.onLanguage(event);
                case "leaderboard" -> leaderboardSetupCommand.onSetupLeaderboard(event);
                case "messages-upload" -> messagesCommand.onUpload(event);
                case "messages-download" -> messagesCommand.onDownload(event);
                case "messages-reset" -> messagesCommand.onReset(event);
                case "start" -> startBasicCommand.onStartBasic(event);
                default -> {}
            }
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        try {
            if (componentId.startsWith("leaderboard:")) {
                leaderboardCommand.onButton(event);
            }
        } catch (Exception e) {
            log.error("Button interaction failed: {}", componentId, e);
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        try {
            if ("setup:start:categories".equals(componentId)) {
                startBasicCommand.onCategorySelect(event);
            }
        } catch (Exception e) {
            log.error("Select menu interaction failed: {}", componentId, e);
        }
    }

    private void replyError(SlashCommandInteractionEvent event) {
        var embed = new EmbedBuilder()
                .setTitle("Something went wrong")
                .setDescription("An error occurred while executing this command. Please try again later.")
                .setColor(Color.RED)
                .build();

        if (event.isAcknowledged()) {
            event.getHook().editOriginalEmbeds(embed).queue();
        } else {
            event.replyEmbeds(embed).setEphemeral(true).queue();
        }
    }
}
