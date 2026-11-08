package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.discordbot.entity.CommunityEventMessage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityEventMessageRepository extends JpaRepository<CommunityEventMessage, String> {

    List<CommunityEventMessage> findByMessageIdIn(Collection<String> messageIds);
}
