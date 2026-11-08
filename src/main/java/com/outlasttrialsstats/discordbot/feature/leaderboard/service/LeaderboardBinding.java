package com.outlasttrialsstats.discordbot.feature.leaderboard.service;

import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import java.util.List;

/**
 * Detached copy of a {@link LeaderboardChannel}. The entity's message ids are a lazy collection, so callers
 * outside the transaction must use this snapshot instead of the entity.
 */
public record LeaderboardBinding(Long id, String guildId, StatisticType category, String channelId,
                                 List<String> messageIds, int maxPages) {

    public LeaderboardBinding {
        messageIds = List.copyOf(messageIds);
    }

    public static LeaderboardBinding of(LeaderboardChannel channel) {
        return new LeaderboardBinding(channel.getId(), channel.getGuildId(), channel.getCategory(),
                channel.getChannelId(), channel.getMessageIds(), channel.getMaxPages());
    }
}
