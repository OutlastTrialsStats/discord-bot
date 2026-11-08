package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaderboardChannelRepository extends JpaRepository<LeaderboardChannel, Long> {

    List<LeaderboardChannel> findByGuildId(String guildId);

    Optional<LeaderboardChannel> findByGuildIdAndCategory(String guildId, StatisticType category);

    @Query("SELECT lc FROM LeaderboardChannel lc JOIN FETCH lc.messageIds")
    List<LeaderboardChannel> findAllWithMessageIds();

    @Query("SELECT DISTINCT lc FROM LeaderboardChannel lc JOIN lc.messageIds m WHERE m IN :messageIds")
    List<LeaderboardChannel> findByMessageIdIn(@Param("messageIds") Collection<String> messageIds);

}
