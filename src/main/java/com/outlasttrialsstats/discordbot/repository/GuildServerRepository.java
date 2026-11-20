package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.discordbot.entity.GuildServer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface GuildServerRepository extends JpaRepository<GuildServer, String> {

    @Query("SELECT g.guildId FROM GuildServer g WHERE g.botPresent = true")
    List<String> findGuildIdsWithBotPresent();

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE GuildServer g SET g.botPresent = false WHERE g.guildId = :guildId")
    void markBotAbsent(@Param("guildId") String guildId);
}
