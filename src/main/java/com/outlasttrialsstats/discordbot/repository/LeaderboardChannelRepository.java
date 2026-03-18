package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaderboardChannelRepository extends JpaRepository<LeaderboardChannel, Long> {

    List<LeaderboardChannel> findByGuildId(String guildId);

    Optional<LeaderboardChannel> findByGuildIdAndCategory(String guildId, StatisticType category);

    void deleteByGuildIdAndCategory(String guildId, StatisticType category);
}
