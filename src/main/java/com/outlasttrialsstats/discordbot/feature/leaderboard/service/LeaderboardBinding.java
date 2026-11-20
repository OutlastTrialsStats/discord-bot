package com.outlasttrialsstats.discordbot.feature.leaderboard.service;

import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import java.util.List;

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
