package com.outlasttrialsstats.discordbot.feature.widget;

import com.outlasttrialsstats.discordbot.entity.WidgetEnrollment;
import com.outlasttrialsstats.discordbot.entity.WidgetStatus;
import com.outlasttrialsstats.discordbot.feature.widget.config.WidgetProperties;
import com.outlasttrialsstats.discordbot.feature.widget.dto.WidgetPushResult;
import com.outlasttrialsstats.discordbot.feature.widget.service.WidgetPushService;
import com.outlasttrialsstats.discordbot.repository.WidgetEnrollmentRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WidgetSyncScheduler {

    private final WidgetEnrollmentRepository enrollmentRepository;
    private final WidgetPushService pushService;
    private final WidgetProperties widgetProperties;

    // Offset from RoleSyncScheduler's cadence so both hourly jobs do not start together.
    @Scheduled(fixedRate = 60, initialDelay = 30, timeUnit = TimeUnit.MINUTES)
    public void syncAllWidgets() {
        if (!widgetProperties.enabled()) {
            return;
        }

        List<WidgetEnrollment> active = enrollmentRepository.findByStatus(WidgetStatus.ACTIVE);
        if (active.isEmpty()) {
            return;
        }

        log.info("Starting scheduled widget sync for {} enrollments", active.size());

        int updated = 0;
        int revoked = 0;
        int failed = 0;

        for (WidgetEnrollment enrollment : active) {
            var result = pushService.pushOne(enrollment);
            if (result instanceof WidgetPushResult.RateLimited(long retryAfterSeconds)) {
                sleep(TimeUnit.SECONDS.toMillis(retryAfterSeconds));
                result = pushService.pushOne(enrollment);
            }

            switch (result) {
                case WidgetPushResult.Success _ -> updated++;
                case WidgetPushResult.Revoked _ -> revoked++;
                case WidgetPushResult.RateLimited _, WidgetPushResult.Failed _ -> failed++;
            }

            sleep(widgetProperties.refreshCooldownMs());
        }

        log.info("Scheduled widget sync finished: {} updated, {} revoked, {} failed",
                updated, revoked, failed);
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
