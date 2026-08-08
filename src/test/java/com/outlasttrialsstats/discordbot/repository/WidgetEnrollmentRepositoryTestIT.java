package com.outlasttrialsstats.discordbot.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.outlasttrialsstats.discordbot.IntegrationTest;
import com.outlasttrialsstats.discordbot.entity.WidgetEnrollment;
import com.outlasttrialsstats.discordbot.entity.WidgetStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class WidgetEnrollmentRepositoryTestIT {

    @Autowired
    private WidgetEnrollmentRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findByStatus_returnsOnlyMatching() {
        repository.save(enrollment("user-1", WidgetStatus.ACTIVE, null));
        repository.save(enrollment("user-2", WidgetStatus.ACTIVE, null));
        repository.save(enrollment("user-3", WidgetStatus.REVOKED, null));
        repository.save(enrollment("user-4", WidgetStatus.PENDING, "state-4"));

        var result = repository.findByStatus(WidgetStatus.ACTIVE);

        assertThat(result)
                .hasSize(2)
                .extracting(WidgetEnrollment::getDiscordUserId)
                .containsExactlyInAnyOrder("user-1", "user-2");
    }

    @Test
    void findByOauthState_findsPendingEnrollment() {
        var pending = enrollment("user-1", WidgetStatus.PENDING, "state-abc");
        pending.setStateExpiresAt(Instant.now().plusSeconds(900));
        repository.save(pending);

        var result = repository.findByOauthState("state-abc");

        assertThat(result).isPresent();
        assertThat(result.get().getDiscordUserId()).isEqualTo("user-1");
        assertThat(result.get().getStateExpiresAt()).isNotNull();
    }

    @Test
    void findByOauthState_noMatch_returnsEmpty() {
        repository.save(enrollment("user-1", WidgetStatus.PENDING, "state-abc"));

        var result = repository.findByOauthState("unknown-state");

        assertThat(result).isEmpty();
    }

    @Test
    void save_persistsAllFields() {
        var enrollment = enrollment("user-1", WidgetStatus.ACTIVE, null);
        enrollment.setEnabledAt(Instant.parse("2026-01-01T00:00:00Z"));
        enrollment.setLastPushedAt(Instant.parse("2026-01-02T00:00:00Z"));
        enrollment.setLastError("some error");
        repository.save(enrollment);

        var result = repository.findById("user-1").orElseThrow();

        assertThat(result.getStatus()).isEqualTo(WidgetStatus.ACTIVE);
        assertThat(result.getEnabledAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(result.getLastPushedAt()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(result.getLastError()).isEqualTo("some error");
    }

    private static WidgetEnrollment enrollment(String userId, WidgetStatus status, String oauthState) {
        var enrollment = new WidgetEnrollment(userId);
        enrollment.setStatus(status);
        enrollment.setOauthState(oauthState);
        return enrollment;
    }
}
