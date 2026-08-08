package com.outlasttrialsstats.discordbot.feature.widget.service;

import com.outlasttrialsstats.discordbot.entity.WidgetEnrollment;
import com.outlasttrialsstats.discordbot.entity.WidgetStatus;
import com.outlasttrialsstats.discordbot.feature.widget.dto.WidgetPushResult;
import com.outlasttrialsstats.discordbot.repository.WidgetEnrollmentRepository;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WidgetPushService {

    private final WidgetEnrollmentRepository enrollmentRepository;
    private final TOTStatsApiClient statsApiClient;
    private final WidgetFieldMapper fieldMapper;
    private final DiscordWidgetApiClient widgetApiClient;

    /**
     * Fetches the user's full stats profile and pushes it to their profile widget.
     * Updates the enrollment's status and bookkeeping fields based on the outcome.
     */
    public WidgetPushResult pushOne(WidgetEnrollment enrollment) {
        String userId = enrollment.getDiscordUserId();

        var profile = statsApiClient.getProfile(userId);
        if (profile.isEmpty()) {
            enrollment.setLastError("No linked stats profile");
            enrollmentRepository.save(enrollment);
            return new WidgetPushResult.Failed("No linked stats profile");
        }

        var payload = fieldMapper.toPayload(userId, profile.get());
        var result = widgetApiClient.pushProfile(userId, payload);
        applyResult(enrollment, result);
        return result;
    }

    public WidgetPushResult pushOne(String discordUserId) {
        return enrollmentRepository.findById(discordUserId)
                .map(this::pushOne)
                .orElseGet(() -> new WidgetPushResult.Failed("Not enrolled"));
    }

    private void applyResult(WidgetEnrollment enrollment, WidgetPushResult result) {
        switch (result) {
            case WidgetPushResult.Success _ -> {
                enrollment.setLastPushedAt(Instant.now());
                enrollment.setLastError(null);
            }
            case WidgetPushResult.Revoked(String error) -> {
                enrollment.setStatus(WidgetStatus.REVOKED);
                enrollment.setLastError(truncate(error));
            }
            case WidgetPushResult.RateLimited _ -> { /* transient; retried by the caller, no state change */ }
            case WidgetPushResult.Failed(String error) -> enrollment.setLastError(truncate(error));
        }
        enrollmentRepository.save(enrollment);
    }

    private static String truncate(String error) {
        if (error == null) return null;
        return error.length() > 512 ? error.substring(0, 512) : error;
    }
}
