package com.outlasttrialsstats.discordbot.feature.widget;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.entity.WidgetEnrollment;
import com.outlasttrialsstats.discordbot.entity.WidgetStatus;
import com.outlasttrialsstats.discordbot.feature.widget.config.WidgetProperties;
import com.outlasttrialsstats.discordbot.feature.widget.dto.WidgetPushResult;
import com.outlasttrialsstats.discordbot.feature.widget.service.WidgetPushService;
import com.outlasttrialsstats.discordbot.repository.WidgetEnrollmentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WidgetSyncSchedulerTest {

    @Mock
    private WidgetEnrollmentRepository enrollmentRepository;

    @Mock
    private WidgetPushService pushService;

    @Test
    void syncAllWidgets_disabled_doesNothing() {
        var scheduler = scheduler(false);

        scheduler.syncAllWidgets();

        verifyNoInteractions(enrollmentRepository, pushService);
    }

    @Test
    void syncAllWidgets_noActiveEnrollments_doesNotPush() {
        var scheduler = scheduler(true);
        when(enrollmentRepository.findByStatus(WidgetStatus.ACTIVE)).thenReturn(List.of());

        scheduler.syncAllWidgets();

        verify(pushService, never()).pushOne(any(WidgetEnrollment.class));
    }

    @Test
    void syncAllWidgets_pushesEachActiveEnrollment() {
        var scheduler = scheduler(true);
        var first = enrollment("user-1");
        var second = enrollment("user-2");
        when(enrollmentRepository.findByStatus(WidgetStatus.ACTIVE)).thenReturn(List.of(first, second));
        when(pushService.pushOne(any(WidgetEnrollment.class))).thenReturn(new WidgetPushResult.Success());

        scheduler.syncAllWidgets();

        verify(pushService).pushOne(first);
        verify(pushService).pushOne(second);
    }

    @Test
    void syncAllWidgets_rateLimited_retriesOnce() {
        var scheduler = scheduler(true);
        var enrollment = enrollment("user-1");
        when(enrollmentRepository.findByStatus(WidgetStatus.ACTIVE)).thenReturn(List.of(enrollment));
        when(pushService.pushOne(enrollment))
                .thenReturn(new WidgetPushResult.RateLimited(0))
                .thenReturn(new WidgetPushResult.Success());

        scheduler.syncAllWidgets();

        verify(pushService, times(2)).pushOne(enrollment);
    }

    private WidgetSyncScheduler scheduler(boolean enabled) {
        var properties = new WidgetProperties(enabled, "https://bot.example.com", 0);
        return new WidgetSyncScheduler(enrollmentRepository, pushService, properties);
    }

    private static WidgetEnrollment enrollment(String userId) {
        var enrollment = new WidgetEnrollment(userId);
        enrollment.setStatus(WidgetStatus.ACTIVE);
        return enrollment;
    }
}
