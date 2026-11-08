package com.outlasttrialsstats.discordbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "community_event_alert_rule", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"guild_id", "alert_type"})
})
@Getter
@Setter
@NoArgsConstructor
public class CommunityEventAlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private CommunityAlertType alertType;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "role_id")
    private String roleId;

    @Column(name = "lead_time_minutes")
    private Integer leadTimeMinutes;

    public CommunityEventAlertRule(String guildId, CommunityAlertType alertType) {
        this.guildId = guildId;
        this.alertType = alertType;
        this.enabled = alertType.isEnabledByDefault();
        this.leadTimeMinutes = alertType.getDefaultLeadTimeMinutes();
    }
}
