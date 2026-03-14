package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.discordbot.entity.GuildMessage;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuildMessageRepository extends JpaRepository<GuildMessage, Long> {

    List<GuildMessage> findByGuildId(String guildId);

    void deleteByGuildId(String guildId);
}
