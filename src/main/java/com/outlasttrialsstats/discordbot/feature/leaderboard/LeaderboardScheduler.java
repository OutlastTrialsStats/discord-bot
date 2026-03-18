package com.outlasttrialsstats.discordbot.feature.leaderboard;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LeaderboardScheduler {

    private final JDA jda;
    private final LeaderboardService leaderboardService;

    @Scheduled(fixedRate = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    public void updateLeaderboards() {
        log.info("Starting scheduled leaderboard update");

        for (LeaderboardChannel binding : leaderboardService.getAllBindings()) {
            try {
                updateBinding(binding);
            } catch (Exception e) {
                log.warn("Failed to update leaderboard for guild {} category {}: {}",
                        binding.getGuildId(), binding.getCategory(), e.getMessage());
            }
        }
    }

    private void updateBinding(LeaderboardChannel binding) {
        TextChannel channel = jda.getTextChannelById(binding.getChannelId());
        if (channel == null) {
            log.info("Channel {} no longer exists, removing leaderboard binding", binding.getChannelId());
            leaderboardService.removeBinding(binding.getGuildId(), binding.getCategory());
            return;
        }

        Optional<DiscordLeaderboardResponse> response = leaderboardService
                .fetchLeaderboard(binding.getCategory(), 1);
        if (response.isEmpty()) {
            log.warn("Failed to fetch leaderboard data for guild {} category {}",
                    binding.getGuildId(), binding.getCategory());
            return;
        }

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                binding.getGuildId(), binding.getCategory(), response.get(), true);

        channel.editMessageEmbedsById(binding.getMessageId(), embed).queue(
                _ -> log.debug("Updated leaderboard in guild {} for category {}",
                        binding.getGuildId(), binding.getCategory()),
                error -> {
                    log.info("Message {} no longer exists, removing leaderboard binding: {}",
                            binding.getMessageId(), error.getMessage());
                    leaderboardService.removeBinding(binding.getGuildId(), binding.getCategory());
                }
        );
    }
}
