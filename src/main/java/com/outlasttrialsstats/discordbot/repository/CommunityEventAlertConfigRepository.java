package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.discordbot.entity.CommunityEventAlertConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityEventAlertConfigRepository extends JpaRepository<CommunityEventAlertConfig, String> {
}
