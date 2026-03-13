package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.discordbot.entity.GuildSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuildSettingsRepository extends JpaRepository<GuildSettings, String> {}
