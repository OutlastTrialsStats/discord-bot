package com.outlasttrialsstats.discordbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "community_event_alert_state", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"guild_id", "event_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class CommunityEventAlertState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "last_reached_index", nullable = false)
    private int lastReachedIndex;

    @Column(name = "pre_alerted_index", nullable = false)
    private int preAlertedIndex;

    public CommunityEventAlertState(String guildId, String eventId, int lastReachedIndex, int preAlertedIndex) {
        this.guildId = guildId;
        this.eventId = eventId;
        this.lastReachedIndex = lastReachedIndex;
        this.preAlertedIndex = preAlertedIndex;
    }
}
