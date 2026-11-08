package com.outlasttrialsstats.discordbot.feature.leaderboard;

import java.util.Collection;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * Best-effort deletion of leaderboard messages: failures are only logged, because the messages may already be gone.
 */
@Slf4j
public final class LeaderboardMessageCleanup {

    private LeaderboardMessageCleanup() {
    }

    /**
     * Queues the deletion of {@code messageIds} in {@code channel}, leaving out the ids in {@code skip}.
     */
    public static void deleteMessages(MessageChannel channel, Collection<String> messageIds, Set<String> skip) {
        for (String messageId : messageIds) {
            if (skip.contains(messageId)) {
                continue;
            }
            try {
                channel.deleteMessageById(messageId).queue(
                        _ -> {},
                        error -> log.debug("Could not delete leaderboard message {}: {}",
                                messageId, error.getMessage()));
            } catch (Exception e) {
                log.debug("Could not delete leaderboard message {}: {}", messageId, e.getMessage());
            }
        }
    }
}
