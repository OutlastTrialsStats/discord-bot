package com.outlasttrialsstats.discordbot.feature.leaderboard;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
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

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
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

        List<String> messageIds = binding.getMessageIds();
        int maxPages = binding.getMaxPages();

        for (int i = 0; i < maxPages && i < messageIds.size(); i++) {
            int page = i + 1;
            Optional<DiscordLeaderboardResponse> response = leaderboardService
                    .fetchLeaderboard(binding.getCategory(), page);
            if (response.isEmpty()) {
                log.warn("Failed to fetch leaderboard page {} for guild {} category {}",
                        page, binding.getGuildId(), binding.getCategory());
                continue;
            }

            MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                    binding.getGuildId(), jda.getGuildById(binding.getGuildId()),
                    binding.getCategory(), response.get(), page == 1, true, false);

            String messageId = messageIds.get(i);
            channel.editMessageEmbedsById(messageId, embed).queue(
                    _ -> log.debug("Updated leaderboard page {} in guild {} for category {}",
                            page, binding.getGuildId(), binding.getCategory()),
                    error -> {
                        if (error instanceof ErrorResponseException ere
                                && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                            log.info("Message {} no longer exists, removing leaderboard binding", messageId);
                            leaderboardService.removeBinding(binding.getGuildId(), binding.getCategory());
                        } else {
                            log.warn("Failed to update leaderboard message {}: {}", messageId, error.getMessage());
                        }
                    }
            );
        }
    }
}
