package com.outlasttrialsstats.discordbot.command;

import com.outlasttrialsstats.discordbot.feature.communityevent.command.CommunityEventCommand;
import com.outlasttrialsstats.discordbot.feature.leaderboard.command.LeaderboardCommand;
import com.outlasttrialsstats.discordbot.feature.profile.command.ProfileCommand;
import com.outlasttrialsstats.discordbot.feature.profile.command.SyncAllCommand;
import com.outlasttrialsstats.discordbot.feature.setup.command.StartBasicCommand;
import com.outlasttrialsstats.discordbot.feature.setup.panel.PanelAction;
import com.outlasttrialsstats.discordbot.feature.setup.panel.SetupPanel;
import java.awt.Color;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommandRouter extends ListenerAdapter {

    private final ProfileCommand profileCommand;
    private final SyncAllCommand syncAllCommand;
    private final StartBasicCommand startBasicCommand;
    private final LeaderboardCommand leaderboardCommand;
    private final CommunityEventCommand communityEventCommand;
    private final SetupPanel setupPanel;

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        try {
            if (CommunityEventCommand.NAME.equals(event.getName())) {
                communityEventCommand.onAutoComplete(event);
            } else if ("leaderboard".equals(event.getName())
                    && "category".equals(event.getFocusedOption().getName())) {
                leaderboardCommand.onCategoryAutoComplete(event);
            }
        } catch (Exception e) {
            log.error("Autocomplete failed: {}", event.getFullCommandName(), e);
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        try {
            switch (event.getName()) {
                case "sync-profile" -> profileCommand.onProfileUpdate(event);
                case "sync-all" -> syncAllCommand.onSyncAll(event);
                case "leaderboard" -> leaderboardCommand.onLeaderboard(event);
                case CommunityEventCommand.NAME -> communityEventCommand.onEvent(event);
                case "setup" -> setupPanel.open(event);
                default -> {}
            }
        } catch (Exception e) {
            log.error("Command execution failed: {}", event.getFullCommandName(), e);
            replyError(event);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        try {
            if (componentId.startsWith("leaderboard:")) {
                leaderboardCommand.onButton(event);
            } else if (componentId.startsWith(StartBasicCommand.COMPONENT_PREFIX)) {
                startBasicCommand.onButton(event);
            } else if (PanelAction.matches(componentId)) {
                setupPanel.onButton(event);
            }
        } catch (Exception e) {
            log.error("Button interaction failed: {}", componentId, e);
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        try {
            if (StartBasicCommand.CATEGORIES.equals(componentId)) {
                startBasicCommand.onCategorySelect(event);
            } else if (PanelAction.matches(componentId)) {
                setupPanel.onStringSelect(event);
            }
        } catch (Exception e) {
            log.error("Select menu interaction failed: {}", componentId, e);
        }
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String componentId = event.getComponentId();
        try {
            if (StartBasicCommand.EVENT_CHANNEL.equals(componentId)) {
                startBasicCommand.onEventChannelSelect(event);
            } else if (PanelAction.matches(componentId)) {
                setupPanel.onEntitySelect(event);
            }
        } catch (Exception e) {
            log.error("Select menu interaction failed: {}", componentId, e);
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        try {
            if (PanelAction.matches(modalId)) {
                setupPanel.onModal(event);
            }
        } catch (Exception e) {
            log.error("Modal interaction failed: {}", modalId, e);
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
