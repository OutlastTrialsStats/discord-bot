package com.outlasttrialsstats.discordbot.feature.leaderboard.command;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import io.github.kaktushose.jdac.annotations.interactions.Button;
import io.github.kaktushose.jdac.annotations.interactions.Choices;
import io.github.kaktushose.jdac.annotations.interactions.Command;
import io.github.kaktushose.jdac.annotations.interactions.Interaction;
import io.github.kaktushose.jdac.annotations.interactions.Param;
import io.github.kaktushose.jdac.dispatching.events.interactions.CommandEvent;
import io.github.kaktushose.jdac.dispatching.events.interactions.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.springframework.stereotype.Component;

@Interaction
@Component
@RequiredArgsConstructor
public class LeaderboardCommand {

    private final LeaderboardService leaderboardService;
    private final MessageService messageService;

    private int currentPage = 1;
    private StatisticType currentCategory;
    private int totalPages = 1;

    @Command(value = "leaderboard", desc = "View the leaderboard for a stat category")
    public void onLeaderboard(CommandEvent event,
                              @Choices({"completed-trials", "reagents-released", "trials-in-hours",
                                      "escalation-peak", "failed-trials", "deaths", "prestige", "stamps",
                                      "event-tokens", "chess-wins", "chess-rating", "armwrestling-wins",
                                      "armwrestling-loses", "armwrestling-rating", "stroop-rating",
                                      "tennis-wins", "tennis-loses", "tennis-rating",
                                      "invasion-imposter-won-matches", "invasion-imposter-lost-matches",
                                      "invasion-reagent-won-matches", "invasion-reagent-lost-matches"})
                              @Param("Statistic category") String category) {
        String guildId = event.getGuild().getId();
        String enumValue = category.replace("-", "_").toUpperCase();
        currentCategory = StatisticType.fromValue(enumValue);
        currentPage = 1;

        Optional<DiscordLeaderboardResponse> response = leaderboardService.fetchLeaderboard(currentCategory, currentPage);
        if (response.isEmpty()) {
            event.with().ephemeral(true)
                    .reply(messageService.getMessage(guildId, "leaderboard.error"));
            return;
        }

        DiscordLeaderboardResponse data = response.get();
        totalPages = data.getTotalPages() != null ? data.getTotalPages() : 1;
        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(guildId, event.getGuild(), currentCategory, data, false);

        event.with().ephemeral(true)
                .builder(builder -> builder.addEmbeds(embed))
                .components(getButtons())
                .reply();
    }

    @Button(value = "Previous", style = ButtonStyle.SECONDARY)
    public void onPrevious(ComponentEvent event) {
        if (currentPage <= 1) {
            event.jdaEvent().deferEdit().queue();
            return;
        }
        currentPage--;
        updatePage(event);
    }

    @Button(value = "Next", style = ButtonStyle.SECONDARY)
    public void onNext(ComponentEvent event) {
        if (currentPage >= totalPages) {
            event.jdaEvent().deferEdit().queue();
            return;
        }
        currentPage++;
        updatePage(event);
    }

    private void updatePage(ComponentEvent event) {
        String guildId = event.getGuild().getId();
        Optional<DiscordLeaderboardResponse> response = leaderboardService.fetchLeaderboard(currentCategory, currentPage);
        if (response.isEmpty()) {
            event.with().editReply(true)
                    .reply(messageService.getMessage(guildId, "leaderboard.error"));
            return;
        }

        DiscordLeaderboardResponse data = response.get();
        totalPages = data.getTotalPages() != null ? data.getTotalPages() : 1;
        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(guildId, event.getGuild(), currentCategory, data, false);
        event.with().editReply(true).keepComponents(false)
                .builder(builder -> builder.addEmbeds(embed))
                .components(getButtons())
                .reply();
    }

    private String[] getButtons() {
        List<String> buttons = new ArrayList<>();
        if (currentPage > 1) {
            buttons.add("onPrevious");
        }
        if (currentPage < totalPages) {
            buttons.add("onNext");
        }
        return buttons.toArray(String[]::new);
    }
}
