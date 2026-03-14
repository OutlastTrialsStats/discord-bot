package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.discordbot.entity.GuildServer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuildServerRepository extends JpaRepository<GuildServer, String> {}
