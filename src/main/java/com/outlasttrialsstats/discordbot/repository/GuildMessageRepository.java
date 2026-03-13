package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.discordbot.entity.GuildMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuildMessageRepository extends JpaRepository<GuildMessage, Long> {

    Optional<GuildMessage> findByGuildIdAndMessageKey(String guildId, String messageKey);

    List<GuildMessage> findByGuildId(String guildId);

    void deleteByGuildId(String guildId);
}
