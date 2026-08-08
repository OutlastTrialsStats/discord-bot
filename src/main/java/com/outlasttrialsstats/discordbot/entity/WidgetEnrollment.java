package com.outlasttrialsstats.discordbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "widget_enrollment")
@Getter
@Setter
@NoArgsConstructor
public class WidgetEnrollment {

    @Id
    @Column(name = "discord_user_id")
    private String discordUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WidgetStatus status = WidgetStatus.PENDING;

    @Column(name = "oauth_state", length = 64)
    private String oauthState;

    @Column(name = "state_expires_at")
    private Instant stateExpiresAt;

    @Column(name = "enabled_at")
    private Instant enabledAt;

    @Column(name = "last_pushed_at")
    private Instant lastPushedAt;

    @Column(name = "last_error", length = 512)
    private String lastError;

    public WidgetEnrollment(String discordUserId) {
        this.discordUserId = discordUserId;
    }
}
