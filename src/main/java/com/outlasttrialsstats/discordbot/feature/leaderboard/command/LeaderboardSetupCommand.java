package com.outlasttrialsstats.discordbot.feature.leaderboard.command;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import io.github.kaktushose.jdac.annotations.interactions.Choices;
import io.github.kaktushose.jdac.annotations.interactions.Command;
import io.github.kaktushose.jdac.annotations.interactions.CommandConfig;
import io.github.kaktushose.jdac.annotations.interactions.Interaction;
import io.github.kaktushose.jdac.annotations.interactions.Param;
import io.github.kaktushose.jdac.dispatching.events.interactions.CommandEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Component;

@Interaction
@Component
@RequiredArgsConstructor
@Slf4j
public class LeaderboardSetupCommand {

    private final LeaderboardService leaderboardService;
    private final MessageService messageService;

    @CommandConfig(enabledFor = Permission.MANAGE_CHANNEL)
    @Command(value = "setup leaderboard", desc = "Set up an auto-updating leaderboard in a channel")
    public void onSetupLeaderboard(CommandEvent event,
                                   @Param("Channel to post the leaderboard in") TextChannel channel,
                                   @Choices({"completed-trials", "reagents-released", "trials-in-hours",
                                           "escalation-peak", "failed-trials", "deaths", "prestige", "stamps",
                                           "event-tokens", "chess-wins", "chess-rating", "armwrestling-wins",
                                           "armwrestling-loses", "armwrestling-rating", "stroop-rating",
                                           "tennis-wins", "tennis-loses", "tennis-rating",
                                           "invasion-imposter-won-matches", "invasion-imposter-lost-matches",
                                           "invasion-reagent-won-matches", "invasion-reagent-lost-matches"})
                                   @Param("Statistic category") String category,
                                   @Param("Number of pages to display (1-10)") int pages) {
        String guildId = event.getGuild().getId();
        String enumValue = category.replace("-", "_").toUpperCase();
        StatisticType statisticType = StatisticType.fromValue(enumValue);
        String categoryName = leaderboardService.getCategoryDisplayName(guildId, statisticType);
        int maxPages = Math.max(1, Math.min(10, pages));

        // Verify first page is available
        Optional<DiscordLeaderboardResponse> firstResponse = leaderboardService.fetchLeaderboard(statisticType, 1);
        if (firstResponse.isEmpty()) {
            event.with().ephemeral(true)
                    .reply(messageService.getMessage(guildId, "leaderboard.error"));
            return;
        }

        // Cap maxPages to actual total pages
        int totalPages = firstResponse.get().getTotalPages() != null ? firstResponse.get().getTotalPages() : 1;
        maxPages = Math.min(maxPages, totalPages);

        // Delete old messages if binding exists
        Optional<LeaderboardChannel> oldBinding = leaderboardService.removeBinding(guildId, statisticType);
        oldBinding.ifPresent(binding -> deleteOldMessages(event, binding));

        // Post embeds for each page
        List<String> messageIds = new ArrayList<>();
        for (int page = 1; page <= maxPages; page++) {
            Optional<DiscordLeaderboardResponse> response = page == 1
                    ? firstResponse
                    : leaderboardService.fetchLeaderboard(statisticType, page);
            if (response.isEmpty()) break;

            MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                    guildId, event.getGuild(), statisticType, response.get(), false);
            var message = channel.sendMessageEmbeds(embed).complete();
            messageIds.add(message.getId());
        }

        leaderboardService.saveBinding(guildId, statisticType, channel.getId(), messageIds, maxPages);
        event.with().ephemeral(true)
                .reply(messageService.getMessage(guildId, "setup.leaderboard.success",
                        categoryName, channel.getAsMention()));
    }

    private void deleteOldMessages(CommandEvent event, LeaderboardChannel binding) {
        try {
            TextChannel oldChannel = event.getGuild().getTextChannelById(binding.getChannelId());
            if (oldChannel != null) {
                for (String messageId : binding.getMessageIds()) {
                    oldChannel.deleteMessageById(messageId).queue(
                            _ -> {},
                            _ -> log.debug("Could not delete old leaderboard message {}", messageId)
                    );
                }
            }
        } catch (Exception e) {
            log.debug("Could not delete old leaderboard messages: {}", e.getMessage());
        }
    }

}
