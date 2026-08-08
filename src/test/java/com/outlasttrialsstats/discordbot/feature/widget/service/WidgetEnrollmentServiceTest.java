package com.outlasttrialsstats.discordbot.feature.widget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import com.outlasttrialsstats.discordbot.entity.WidgetEnrollment;
import com.outlasttrialsstats.discordbot.entity.WidgetStatus;
import com.outlasttrialsstats.discordbot.feature.widget.config.WidgetProperties;
import com.outlasttrialsstats.discordbot.feature.widget.service.WidgetEnrollmentService.BeginResult;
import com.outlasttrialsstats.discordbot.feature.widget.service.WidgetEnrollmentService.CallbackResult;
import com.outlasttrialsstats.discordbot.repository.WidgetEnrollmentRepository;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WidgetEnrollmentServiceTest {

    private static final String USER_ID = "user-1";
    private static final String APP_ID = "app-1";

    @Mock
    private WidgetEnrollmentRepository enrollmentRepository;

    @Mock
    private TOTStatsApiClient statsApiClient;

    @Mock
    private DiscordWidgetApiClient widgetApiClient;

    private WidgetEnrollmentService service;

    @BeforeEach
    void setUp() {
        var properties = new WidgetProperties(true, "https://bot.example.com", 1500);
        service = new WidgetEnrollmentService(
                enrollmentRepository, statsApiClient, widgetApiClient, properties, APP_ID);
    }

    @Test
    void beginEnrollment_notVerified_returnsNotVerified() {
        when(statsApiClient.getProfile(USER_ID)).thenReturn(Optional.empty());

        var result = service.beginEnrollment(USER_ID);

        assertThat(result).isInstanceOf(BeginResult.NotVerified.class);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void beginEnrollment_alreadyActive_returnsAlreadyActive() {
        when(statsApiClient.getProfile(USER_ID)).thenReturn(Optional.of(new DiscordProfileResponse()));
        var enrollment = new WidgetEnrollment(USER_ID);
        enrollment.setStatus(WidgetStatus.ACTIVE);
        when(enrollmentRepository.findById(USER_ID)).thenReturn(Optional.of(enrollment));

        var result = service.beginEnrollment(USER_ID);

        assertThat(result).isInstanceOf(BeginResult.AlreadyActive.class);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void beginEnrollment_ready_savesPendingStateAndBuildsUrl() {
        when(statsApiClient.getProfile(USER_ID)).thenReturn(Optional.of(new DiscordProfileResponse()));
        when(enrollmentRepository.findById(USER_ID)).thenReturn(Optional.empty());

        var result = service.beginEnrollment(USER_ID);

        var captor = ArgumentCaptor.forClass(WidgetEnrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getDiscordUserId()).isEqualTo(USER_ID);
        assertThat(saved.getStatus()).isEqualTo(WidgetStatus.PENDING);
        assertThat(saved.getOauthState()).isNotBlank();
        assertThat(saved.getStateExpiresAt()).isAfter(Instant.now());

        assertThat(result).isInstanceOf(BeginResult.Ready.class);
        assertThat(((BeginResult.Ready) result).authorizeUrl())
                .startsWith("https://discord.com/oauth2/authorize?client_id=" + APP_ID)
                .contains("scope=openid%20sdk.social_layer")
                .contains("redirect_uri=https%3A%2F%2Fbot.example.com%2Foauth%2Fwidget%2Fcallback")
                .contains("state=" + saved.getOauthState())
                .contains("prompt=consent");
    }

    @Test
    void completeEnrollment_unknownState_isInvalid() {
        when(enrollmentRepository.findByOauthState("bad")).thenReturn(Optional.empty());

        var result = service.completeEnrollment("code", "bad");

        assertThat(result).isInstanceOf(CallbackResult.InvalidState.class);
    }

    @Test
    void completeEnrollment_expiredState_isExpired() {
        var enrollment = pendingEnrollment(Instant.now().minusSeconds(60));
        when(enrollmentRepository.findByOauthState("state-1")).thenReturn(Optional.of(enrollment));

        var result = service.completeEnrollment("code", "state-1");

        assertThat(result).isInstanceOf(CallbackResult.Expired.class);
        verify(widgetApiClient, never()).exchangeCode(any());
    }

    @Test
    void completeEnrollment_exchangeFails_isExchangeFailed() {
        var enrollment = pendingEnrollment(Instant.now().plusSeconds(600));
        when(enrollmentRepository.findByOauthState("state-1")).thenReturn(Optional.of(enrollment));
        when(widgetApiClient.exchangeCode("code")).thenReturn(Optional.empty());

        var result = service.completeEnrollment("code", "state-1");

        assertThat(result).isInstanceOf(CallbackResult.ExchangeFailed.class);
    }

    @Test
    void completeEnrollment_differentUserAuthorized_isUserMismatch() {
        var enrollment = pendingEnrollment(Instant.now().plusSeconds(600));
        when(enrollmentRepository.findByOauthState("state-1")).thenReturn(Optional.of(enrollment));
        when(widgetApiClient.exchangeCode("code")).thenReturn(Optional.of("token"));
        when(widgetApiClient.fetchAuthorizedUserId("token")).thenReturn(Optional.of("other-user"));

        var result = service.completeEnrollment("code", "state-1");

        assertThat(result).isInstanceOf(CallbackResult.UserMismatch.class);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void completeEnrollment_success_activatesEnrollment() {
        var enrollment = pendingEnrollment(Instant.now().plusSeconds(600));
        when(enrollmentRepository.findByOauthState("state-1")).thenReturn(Optional.of(enrollment));
        when(widgetApiClient.exchangeCode("code")).thenReturn(Optional.of("token"));
        when(widgetApiClient.fetchAuthorizedUserId("token")).thenReturn(Optional.of(USER_ID));

        var result = service.completeEnrollment("code", "state-1");

        assertThat(result).isEqualTo(new CallbackResult.Success(USER_ID));
        assertThat(enrollment.getStatus()).isEqualTo(WidgetStatus.ACTIVE);
        assertThat(enrollment.getOauthState()).isNull();
        assertThat(enrollment.getStateExpiresAt()).isNull();
        assertThat(enrollment.getEnabledAt()).isNotNull();
        verify(enrollmentRepository).save(enrollment);
    }

    @Test
    void disable_activeEnrollment_clearsWidgetData() {
        var enrollment = new WidgetEnrollment(USER_ID);
        enrollment.setStatus(WidgetStatus.ACTIVE);
        when(enrollmentRepository.findById(USER_ID)).thenReturn(Optional.of(enrollment));

        boolean result = service.disable(USER_ID);

        assertThat(result).isTrue();
        assertThat(enrollment.getStatus()).isEqualTo(WidgetStatus.DISABLED);
        verify(widgetApiClient).clearProfile(USER_ID);
    }

    @Test
    void disable_pendingEnrollment_doesNotCallDiscord() {
        var enrollment = pendingEnrollment(Instant.now().plusSeconds(600));
        when(enrollmentRepository.findById(USER_ID)).thenReturn(Optional.of(enrollment));

        boolean result = service.disable(USER_ID);

        assertThat(result).isTrue();
        verify(widgetApiClient, never()).clearProfile(any());
    }

    @Test
    void disable_noEnrollment_returnsFalse() {
        when(enrollmentRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.disable(USER_ID)).isFalse();
    }

    private static WidgetEnrollment pendingEnrollment(Instant stateExpiresAt) {
        var enrollment = new WidgetEnrollment(USER_ID);
        enrollment.setStatus(WidgetStatus.PENDING);
        enrollment.setOauthState("state-1");
        enrollment.setStateExpiresAt(stateExpiresAt);
        return enrollment;
    }
}
