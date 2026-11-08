package com.outlasttrialsstats.discordbot.feature.leaderboard;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardBinding;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LeaderboardScheduler {

    private static final long EDIT_COOLDOWN_SECONDS = 6;
    /** Channels are updated in parallel, but never by more than this many threads at once. */
    private static final int UPDATE_THREADS = 2;

    private final JDA jda;
    private final LeaderboardService leaderboardService;
    private final Executor executor;

    @Autowired
    public LeaderboardScheduler(JDA jda, LeaderboardService leaderboardService) {
        this(jda, leaderboardService, Executors.newFixedThreadPool(UPDATE_THREADS, Thread.ofPlatform()
                .name("leaderboard-update-", 0)
                .daemon(true)
                .factory()));
    }

    LeaderboardScheduler(JDA jda, LeaderboardService leaderboardService, Executor executor) {
        this.jda = jda;
        this.leaderboardService = leaderboardService;
        this.executor = executor;
    }

    @PreDestroy
    void shutdown() {
        if (executor instanceof ExecutorService executorService) {
            executorService.shutdownNow();
        }
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void updateLeaderboards() {
        log.info("Starting scheduled leaderboard update");

        leaderboardService.getAllBindings().stream()
                .collect(Collectors.groupingBy(LeaderboardBinding::channelId))
                .forEach(this::processChannelBindings);
    }

    private void processChannelBindings(String channelId, List<LeaderboardBinding> bindings) {
        StandardGuildMessageChannel channel = jda.getChannelById(StandardGuildMessageChannel.class, channelId);
        if (channel == null) {
            for (LeaderboardBinding binding : bindings) {
                if (jda.getUnavailableGuilds().contains(binding.guildId())) {
                    log.info("Guild {} is temporarily unavailable, skipping leaderboard update for channel {}",
                            binding.guildId(), channelId);
                    continue;
                }
                log.info("Channel {} no longer exists, removing leaderboard binding for guild {} category {}",
                        channelId, binding.guildId(), binding.category());
                leaderboardService.removeBindingIfInChannel(binding.id(), channelId);
            }
            return;
        }
        if (channel.isObfuscated() || !channel.getGuild().getSelfMember().hasAccess(channel)) {
            log.warn("No access to channel {}, skipping leaderboard update", channelId);
            return;
        }

        Set<LeaderboardBinding> removedBindings = ConcurrentHashMap.newKeySet();
        CompletableFuture<?> chain = CompletableFuture.completedFuture(null);
        for (LeaderboardBinding binding : bindings) {
            List<String> messageIds = binding.messageIds();
            for (int i = 0; i < binding.maxPages() && i < messageIds.size(); i++) {
                int page = i + 1;
                String messageId = messageIds.get(i);
                chain = chain.thenComposeAsync(_ -> removedBindings.contains(binding)
                        ? CompletableFuture.completedFuture(null)
                        : editLeaderboardMessage(channel, binding, messageId, page, removedBindings), executor);
            }
        }
        chain.whenComplete((_, error) -> {
            if (error != null) {
                log.warn("Leaderboard update for channel {} stopped unexpectedly", channelId, error);
            } else {
                log.debug("Finished leaderboard update for channel {}", channelId);
            }
        });
    }

    /**
     * Updates one page. The returned future always completes normally, so a failing page never stops the
     * remaining pages of the channel.
     */
    private CompletableFuture<?> editLeaderboardMessage(StandardGuildMessageChannel channel, LeaderboardBinding binding,
                                                         String messageId, int page,
                                                         Set<LeaderboardBinding> removedBindings) {
        CompletableFuture<?> edit;
        try {
            Optional<DiscordLeaderboardResponse> response = leaderboardService
                    .fetchLeaderboard(binding.category(), page);
            if (response.isEmpty()) {
                log.warn("Failed to fetch leaderboard page {} for guild {} category {}",
                        page, binding.guildId(), binding.category());
                return CompletableFuture.completedFuture(null);
            }

            MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                    binding.guildId(), jda.getGuildById(binding.guildId()),
                    binding.category(), response.get(), page == 1, true, false);
            edit = channel.editMessageEmbedsById(messageId, embed).submit();
        } catch (Exception e) {
            log.warn("Failed to update leaderboard page {} for guild {} category {}: {}",
                    page, binding.guildId(), binding.category(), e.getMessage());
            return CompletableFuture.completedFuture(null);
        }

        return edit
                .thenAccept(_ -> log.debug("Updated leaderboard page {} in guild {} for category {}",
                        page, binding.guildId(), binding.category()))
                .exceptionallyAsync(error -> {
                    handleEditFailure(channel, binding, messageId, error, removedBindings);
                    return null;
                }, executor)
                .thenCompose(_ -> delay());
    }

    private void handleEditFailure(StandardGuildMessageChannel channel, LeaderboardBinding binding, String messageId,
                                   Throwable error, Set<LeaderboardBinding> removedBindings) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause() : error;
        if (!(cause instanceof ErrorResponseException ere) || ere.getErrorResponse() != ErrorResponse.UNKNOWN_MESSAGE) {
            log.warn("Failed to update leaderboard message {}: {}", messageId, cause.getMessage());
            return;
        }

        try {
            Optional<LeaderboardBinding> removed = leaderboardService
                    .removeBindingIfContainsMessage(binding.id(), messageId);
            if (removed.isEmpty()) {
                log.debug("Message {} no longer exists, but its binding was already replaced", messageId);
                return;
            }
            log.info("Message {} no longer exists, removed leaderboard binding for guild {} category {}",
                    messageId, binding.guildId(), binding.category());
            removedBindings.add(binding);
            deleteRemainingMessages(channel, removed.get(), messageId);
        } catch (Exception e) {
            log.warn("Failed to remove leaderboard binding for message {}: {}", messageId, e.getMessage());
        }
    }

    private void deleteRemainingMessages(StandardGuildMessageChannel channel, LeaderboardBinding binding,
                                         String missingMessageId) {
        if (!binding.channelId().equals(channel.getId())) {
            return;
        }
        for (String messageId : binding.messageIds()) {
            if (messageId.equals(missingMessageId)) {
                continue;
            }
            channel.deleteMessageById(messageId).queue(
                    _ -> {},
                    e -> log.debug("Could not delete leaderboard message {}: {}", messageId, e.getMessage()));
        }
    }

    CompletableFuture<Void> delay() {
        return CompletableFuture.runAsync(() -> {},
                CompletableFuture.delayedExecutor(EDIT_COOLDOWN_SECONDS, TimeUnit.SECONDS, executor));
    }
}
