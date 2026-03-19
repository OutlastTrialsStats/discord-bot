package com.outlasttrialsstats.discordbot.feature.leaderboard.command;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LeaderboardCommand {

    private final LeaderboardService leaderboardService;
    private final MessageService messageService;

    public void onLeaderboard(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String categoryValue = event.getOption("category").getAsString();
        StatisticType category = StatisticType.fromValue(categoryValue);

        Optional<DiscordLeaderboardResponse> response = leaderboardService.fetchLeaderboard(category, 1);
        if (response.isEmpty()) {
            event.reply(messageService.getMessage(guildId, "leaderboard.error"))
                    .setEphemeral(true).queue();
            return;
        }

        DiscordLeaderboardResponse data = response.get();
        int totalPages = data.getTotalPages() != null ? data.getTotalPages() : 1;
        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(guildId, event.getGuild(), category, data, true);

        var reply = event.replyEmbeds(embed).setEphemeral(true);
        List<Button> buttons = buildButtons(categoryValue, 1, totalPages);
        if (!buttons.isEmpty()) {
            reply.addComponents(ActionRow.of(buttons));
        }
        reply.queue();
    }

    public void onButton(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        if (parts.length != 4) return;

        String categoryValue = parts[2];
        int targetPage = Integer.parseInt(parts[3]);
        StatisticType category = StatisticType.fromValue(categoryValue);
        String guildId = event.getGuild().getId();

        Optional<DiscordLeaderboardResponse> response = leaderboardService.fetchLeaderboard(category, targetPage);
        if (response.isEmpty()) {
            event.reply(messageService.getMessage(guildId, "leaderboard.error"))
                    .setEphemeral(true).queue();
            return;
        }

        DiscordLeaderboardResponse data = response.get();
        int totalPages = data.getTotalPages() != null ? data.getTotalPages() : 1;
        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(guildId, event.getGuild(), category, data, true);

        List<Button> buttons = buildButtons(categoryValue, targetPage, totalPages);
        var edit = event.editMessageEmbeds(embed);
        if (!buttons.isEmpty()) {
            edit.setComponents(ActionRow.of(buttons));
        } else {
            edit.setComponents();
        }
        edit.queue();
    }

    private List<Button> buildButtons(String categoryValue, int currentPage, int totalPages) {
        List<Button> buttons = new ArrayList<>();
        if (currentPage > 1) {
            buttons.add(Button.of(ButtonStyle.SECONDARY,
                    "leaderboard:prev:" + categoryValue + ":" + (currentPage - 1), "Previous"));
        }
        if (currentPage < totalPages) {
            buttons.add(Button.of(ButtonStyle.SECONDARY,
                    "leaderboard:next:" + categoryValue + ":" + (currentPage + 1), "Next"));
        }
        return buttons;
    }
}
