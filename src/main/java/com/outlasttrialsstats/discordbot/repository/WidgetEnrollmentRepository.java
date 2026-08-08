package com.outlasttrialsstats.discordbot.repository;

import com.outlasttrialsstats.discordbot.entity.WidgetEnrollment;
import com.outlasttrialsstats.discordbot.entity.WidgetStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WidgetEnrollmentRepository extends JpaRepository<WidgetEnrollment, String> {

    List<WidgetEnrollment> findByStatus(WidgetStatus status);

    Optional<WidgetEnrollment> findByOauthState(String oauthState);
}
