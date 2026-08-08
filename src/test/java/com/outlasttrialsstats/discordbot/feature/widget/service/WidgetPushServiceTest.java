package com.outlasttrialsstats.discordbot.feature.widget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import com.outlasttrialsstats.discordbot.entity.WidgetEnrollment;
import com.outlasttrialsstats.discordbot.entity.WidgetStatus;
import com.outlasttrialsstats.discordbot.feature.widget.dto.WidgetProfilePayload;
import com.outlasttrialsstats.discordbot.feature.widget.dto.WidgetPushResult;
import com.outlasttrialsstats.discordbot.repository.WidgetEnrollmentRepository;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WidgetPushServiceTest {

    private static final String USER_ID = "user-1";

    @Mock
    private WidgetEnrollmentRepository enrollmentRepository;

    @Mock
    private TOTStatsApiClient statsApiClient;

    @Mock
    private WidgetFieldMapper fieldMapper;

    @Mock
    private DiscordWidgetApiClient widgetApiClient;

    @InjectMocks
    private WidgetPushService service;

    @Test
    void pushOne_success_updatesLastPushedAt() {
        var enrollment = activeEnrollment();
        var profile = new DiscordProfileResponse();
        var payload = new WidgetProfilePayload("Player", List.of());
        when(statsApiClient.getProfile(USER_ID)).thenReturn(Optional.of(profile));
        when(fieldMapper.toPayload(USER_ID, profile)).thenReturn(payload);
        when(widgetApiClient.pushProfile(USER_ID, payload)).thenReturn(new WidgetPushResult.Success());

        var result = service.pushOne(enrollment);

        assertThat(result.isSuccess()).isTrue();
        assertThat(enrollment.getLastPushedAt()).isNotNull();
        assertThat(enrollment.getLastError()).isNull();
        assertThat(enrollment.getStatus()).isEqualTo(WidgetStatus.ACTIVE);
        verify(enrollmentRepository).save(enrollment);
    }

    @Test
    void pushOne_noStatsProfile_recordsErrorWithoutDiscordCall() {
        var enrollment = activeEnrollment();
        when(statsApiClient.getProfile(USER_ID)).thenReturn(Optional.empty());

        var result = service.pushOne(enrollment);

        assertThat(result).isEqualTo(new WidgetPushResult.Failed("No linked stats profile"));
        assertThat(enrollment.getLastError()).isEqualTo("No linked stats profile");
        assertThat(enrollment.getStatus()).isEqualTo(WidgetStatus.ACTIVE);
        verify(widgetApiClient, never()).pushProfile(any(), any());
        verify(enrollmentRepository).save(enrollment);
    }

    @Test
    void pushOne_revoked_marksEnrollmentRevoked() {
        var enrollment = activeEnrollment();
        var profile = new DiscordProfileResponse();
        var payload = new WidgetProfilePayload("Player", List.of());
        when(statsApiClient.getProfile(USER_ID)).thenReturn(Optional.of(profile));
        when(fieldMapper.toPayload(USER_ID, profile)).thenReturn(payload);
        when(widgetApiClient.pushProfile(USER_ID, payload))
                .thenReturn(new WidgetPushResult.Revoked("403: Missing Access"));

        var result = service.pushOne(enrollment);

        assertThat(result).isInstanceOf(WidgetPushResult.Revoked.class);
        assertThat(enrollment.getStatus()).isEqualTo(WidgetStatus.REVOKED);
        assertThat(enrollment.getLastError()).isEqualTo("403: Missing Access");
    }

    @Test
    void pushOne_rateLimited_leavesEnrollmentUnchanged() {
        var enrollment = activeEnrollment();
        var profile = new DiscordProfileResponse();
        var payload = new WidgetProfilePayload("Player", List.of());
        when(statsApiClient.getProfile(USER_ID)).thenReturn(Optional.of(profile));
        when(fieldMapper.toPayload(USER_ID, profile)).thenReturn(payload);
        when(widgetApiClient.pushProfile(USER_ID, payload)).thenReturn(new WidgetPushResult.RateLimited(6));

        var result = service.pushOne(enrollment);

        assertThat(result).isEqualTo(new WidgetPushResult.RateLimited(6));
        assertThat(enrollment.getStatus()).isEqualTo(WidgetStatus.ACTIVE);
        assertThat(enrollment.getLastPushedAt()).isNull();
        assertThat(enrollment.getLastError()).isNull();
    }

    @Test
    void pushOne_byUserId_notEnrolled_fails() {
        when(enrollmentRepository.findById(USER_ID)).thenReturn(Optional.empty());

        var result = service.pushOne(USER_ID);

        assertThat(result).isEqualTo(new WidgetPushResult.Failed("Not enrolled"));
        verify(statsApiClient, never()).getProfile(eq(USER_ID));
    }

    private static WidgetEnrollment activeEnrollment() {
        var enrollment = new WidgetEnrollment(USER_ID);
        enrollment.setStatus(WidgetStatus.ACTIVE);
        return enrollment;
    }
}
