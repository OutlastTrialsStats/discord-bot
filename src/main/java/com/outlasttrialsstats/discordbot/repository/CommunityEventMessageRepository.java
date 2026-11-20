package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.discordbot.entity.CommunityEventMessage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityEventMessageRepository extends JpaRepository<CommunityEventMessage, Long> {

    List<CommunityEventMessage> findByGuildIdOrderByIdAsc(String guildId);

    Optional<CommunityEventMessage> findByGuildIdAndEventId(String guildId, String eventId);

    Optional<CommunityEventMessage> findByGuildIdAndEventIdIsNull(String guildId);

    Optional<CommunityEventMessage> findByMessageId(String messageId);

    long countByGuildId(String guildId);

    List<CommunityEventMessage> findByMessageIdIn(Collection<String> messageIds);
}
