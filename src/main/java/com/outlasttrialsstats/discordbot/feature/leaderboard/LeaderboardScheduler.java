package com.outlasttrialsstats.discordbot.feature.leaderboard;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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

    private static final long EDIT_COOLDOWN_SECONDS = 3;

    private final JDA jda;
    private final LeaderboardService leaderboardService;

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void updateLeaderboards() {
        log.info("Starting scheduled leaderboard update");

        leaderboardService.getAllBindings().stream()
                .collect(Collectors.groupingBy(LeaderboardChannel::getChannelId))
                .forEach(this::processChannelBindings);
    }

    private void processChannelBindings(String channelId, List<LeaderboardChannel> bindings) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            log.info("Channel {} no longer exists, removing leaderboard bindings", channelId);
            bindings.forEach(b -> leaderboardService.removeBinding(b.getGuildId(), b.getCategory()));
            return;
        }

        CompletableFuture<?> chain = CompletableFuture.completedFuture(null);
        for (LeaderboardChannel binding : bindings) {
            List<String> messageIds = binding.getMessageIds();
            int maxPages = binding.getMaxPages();

            for (int i = 0; i < maxPages && i < messageIds.size(); i++) {
                int page = i + 1;
                String messageId = messageIds.get(i);
                chain = chain.thenCompose(_ -> editLeaderboardMessage(channel, binding, messageId, page));
            }
        }
    }

    private CompletableFuture<?> editLeaderboardMessage(TextChannel channel, LeaderboardChannel binding,
                                                         String messageId, int page) {
        Optional<DiscordLeaderboardResponse> response = leaderboardService
                .fetchLeaderboard(binding.getCategory(), page);
        if (response.isEmpty()) {
            log.warn("Failed to fetch leaderboard page {} for guild {} category {}",
                    page, binding.getGuildId(), binding.getCategory());
            return CompletableFuture.completedFuture(null);
        }

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                binding.getGuildId(), jda.getGuildById(binding.getGuildId()),
                binding.getCategory(), response.get(), page == 1, true, false);

        return channel.editMessageEmbedsById(messageId, embed).submit()
                .thenAccept(_ -> log.debug("Updated leaderboard page {} in guild {} for category {}",
                        page, binding.getGuildId(), binding.getCategory()))
                .exceptionally(error -> {
                    if (error.getCause() instanceof ErrorResponseException ere
                            && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                        log.info("Message {} no longer exists, removing leaderboard binding", messageId);
                        leaderboardService.removeBinding(binding.getGuildId(), binding.getCategory());
                    } else {
                        log.warn("Failed to update leaderboard message {}: {}", messageId, error.getMessage());
                    }
                    return null;
                })
                .thenCompose(_ -> delay());
    }

    private static CompletableFuture<Void> delay() {
        return CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(LeaderboardScheduler.EDIT_COOLDOWN_SECONDS, TimeUnit.SECONDS));
    }
}
