package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.discordbot.entity.CommunityEventAlertState;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityEventAlertStateRepository extends JpaRepository<CommunityEventAlertState, Long> {

    List<CommunityEventAlertState> findByGuildId(String guildId);

    void deleteByGuildId(String guildId);
}
