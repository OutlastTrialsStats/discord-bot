package com.outlasttrialsstats.discordbot.feature.widget.service;

import com.outlasttrialsstats.discordbot.entity.WidgetEnrollment;
import com.outlasttrialsstats.discordbot.entity.WidgetStatus;
import com.outlasttrialsstats.discordbot.feature.widget.config.WidgetProperties;
import com.outlasttrialsstats.discordbot.repository.WidgetEnrollmentRepository;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WidgetEnrollmentService {

    static final Duration STATE_VALIDITY = Duration.ofMinutes(15);

    private final WidgetEnrollmentRepository enrollmentRepository;
    private final TOTStatsApiClient statsApiClient;
    private final DiscordWidgetApiClient widgetApiClient;
    private final WidgetProperties widgetProperties;
    private final String applicationId;

    public WidgetEnrollmentService(WidgetEnrollmentRepository enrollmentRepository,
                                   TOTStatsApiClient statsApiClient,
                                   DiscordWidgetApiClient widgetApiClient,
                                   WidgetProperties widgetProperties,
                                   @Value("${discord.application-id}") String applicationId) {
        this.enrollmentRepository = enrollmentRepository;
        this.statsApiClient = statsApiClient;
        this.widgetApiClient = widgetApiClient;
        this.widgetProperties = widgetProperties;
        this.applicationId = applicationId;
    }

    public sealed interface BeginResult {
        record NotVerified() implements BeginResult {}
        record AlreadyActive() implements BeginResult {}
        record Ready(String authorizeUrl) implements BeginResult {}
    }

    public sealed interface CallbackResult {
        record Success(String discordUserId) implements CallbackResult {}
        record InvalidState() implements CallbackResult {}
        record Expired() implements CallbackResult {}
        record ExchangeFailed() implements CallbackResult {}
        record UserMismatch() implements CallbackResult {}
    }

    public BeginResult beginEnrollment(String discordUserId) {
        if (statsApiClient.getProfile(discordUserId).isEmpty()) {
            return new BeginResult.NotVerified();
        }

        var enrollment = enrollmentRepository.findById(discordUserId)
                .orElseGet(() -> new WidgetEnrollment(discordUserId));
        if (enrollment.getStatus() == WidgetStatus.ACTIVE) {
            return new BeginResult.AlreadyActive();
        }

        String state = UUID.randomUUID().toString();
        enrollment.setStatus(WidgetStatus.PENDING);
        enrollment.setOauthState(state);
        enrollment.setStateExpiresAt(Instant.now().plus(STATE_VALIDITY));
        enrollmentRepository.save(enrollment);

        return new BeginResult.Ready(buildAuthorizeUrl(state));
    }

    public CallbackResult completeEnrollment(String code, String state) {
        var enrollmentOpt = enrollmentRepository.findByOauthState(state);
        if (enrollmentOpt.isEmpty()) {
            return new CallbackResult.InvalidState();
        }

        var enrollment = enrollmentOpt.get();
        if (enrollment.getStateExpiresAt() == null || Instant.now().isAfter(enrollment.getStateExpiresAt())) {
            return new CallbackResult.Expired();
        }

        var accessToken = widgetApiClient.exchangeCode(code);
        if (accessToken.isEmpty()) {
            return new CallbackResult.ExchangeFailed();
        }

        var authorizedUserId = widgetApiClient.fetchAuthorizedUserId(accessToken.get());
        if (authorizedUserId.isEmpty() || !authorizedUserId.get().equals(enrollment.getDiscordUserId())) {
            log.warn("Widget OAuth user mismatch: enrollment {} vs authorized {}",
                    enrollment.getDiscordUserId(), authorizedUserId.orElse("<none>"));
            return new CallbackResult.UserMismatch();
        }

        enrollment.setStatus(WidgetStatus.ACTIVE);
        enrollment.setOauthState(null);
        enrollment.setStateExpiresAt(null);
        enrollment.setEnabledAt(Instant.now());
        enrollment.setLastError(null);
        enrollmentRepository.save(enrollment);

        log.info("Widget enrollment activated for user {}", enrollment.getDiscordUserId());
        return new CallbackResult.Success(enrollment.getDiscordUserId());
    }

    /**
     * Disables the widget and clears its data on Discord (best effort).
     *
     * @return true if the user had an enrollment
     */
    public boolean disable(String discordUserId) {
        var enrollmentOpt = enrollmentRepository.findById(discordUserId);
        if (enrollmentOpt.isEmpty()) {
            return false;
        }

        var enrollment = enrollmentOpt.get();
        boolean wasActive = enrollment.getStatus() == WidgetStatus.ACTIVE;
        enrollment.setStatus(WidgetStatus.DISABLED);
        enrollment.setOauthState(null);
        enrollment.setStateExpiresAt(null);
        enrollmentRepository.save(enrollment);

        if (wasActive) {
            widgetApiClient.clearProfile(discordUserId);
        }
        return true;
    }

    public Optional<WidgetEnrollment> getEnrollment(String discordUserId) {
        return enrollmentRepository.findById(discordUserId);
    }

    private String buildAuthorizeUrl(String state) {
        return "https://discord.com/oauth2/authorize"
                + "?client_id=" + applicationId
                + "&response_type=code"
                + "&scope=" + urlEncode("openid sdk.social_layer")
                + "&redirect_uri=" + urlEncode(widgetProperties.callbackUrl())
                + "&state=" + state
                + "&prompt=consent";
    }

    private static String urlEncode(String value) {
        // URLEncoder is form encoding; query params need %20 instead of + for spaces
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
