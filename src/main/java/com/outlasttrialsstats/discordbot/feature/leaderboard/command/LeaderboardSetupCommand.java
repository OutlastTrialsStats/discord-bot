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
                                   @Param("Statistic category") String category) {
        String guildId = event.getGuild().getId();
        String enumValue = category.replace("-", "_").toUpperCase();
        StatisticType statisticType = StatisticType.fromValue(enumValue);
        String categoryName = leaderboardService.getCategoryDisplayName(guildId, statisticType);

        Optional<DiscordLeaderboardResponse> response = leaderboardService.fetchLeaderboard(statisticType, 1);
        if (response.isEmpty()) {
            event.with().ephemeral(true)
                    .reply(messageService.getMessage(guildId, "leaderboard.error"));
            return;
        }

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(guildId, event.getGuild(), statisticType, response.get(), true);

        // Delete old message if binding exists
        Optional<LeaderboardChannel> oldBinding = leaderboardService.removeBinding(guildId, statisticType);
        oldBinding.ifPresent(binding -> {
            try {
                TextChannel oldChannel = event.getGuild().getTextChannelById(binding.getChannelId());
                if (oldChannel != null) {
                    oldChannel.deleteMessageById(binding.getMessageId()).queue(
                            _ -> {},
                            _ -> log.debug("Could not delete old leaderboard message")
                    );
                }
            } catch (Exception e) {
                log.debug("Could not delete old leaderboard message: {}", e.getMessage());
            }
        });

        var message = channel.sendMessageEmbeds(embed).complete();
        leaderboardService.saveBinding(guildId, statisticType, channel.getId(), message.getId());
        event.with().ephemeral(true)
                .reply(messageService.getMessage(guildId, "setup.leaderboard.success",
                        categoryName, channel.getAsMention()));
    }

    @CommandConfig(enabledFor = Permission.MANAGE_CHANNEL)
    @Command(value = "setup remove-leaderboard", desc = "Remove an auto-updating leaderboard")
    public void onRemoveLeaderboard(CommandEvent event,
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
        StatisticType statisticType = StatisticType.fromValue(enumValue);
        String categoryName = leaderboardService.getCategoryDisplayName(guildId, statisticType);

        Optional<LeaderboardChannel> binding = leaderboardService.removeBinding(guildId, statisticType);
        if (binding.isEmpty()) {
            event.with().ephemeral(true)
                    .reply(messageService.getMessage(guildId, "setup.leaderboard.not_found", categoryName));
            return;
        }

        // Best-effort delete the message
        try {
            TextChannel channel = event.getGuild().getTextChannelById(binding.get().getChannelId());
            if (channel != null) {
                channel.deleteMessageById(binding.get().getMessageId()).queue(
                        _ -> {},
                        _ -> log.debug("Could not delete leaderboard message")
                );
            }
        } catch (Exception e) {
            log.debug("Could not delete leaderboard message: {}", e.getMessage());
        }

        event.with().ephemeral(true)
                .reply(messageService.getMessage(guildId, "setup.leaderboard.removed", categoryName));
    }
}
