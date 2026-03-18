package com.outlasttrialsstats.discordbot.entity;

import com.outlasttrialsstats.backend.api.model.StatisticType;
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
@Table(name = "leaderboard_channel", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"guild_id", "category"})
})
@Getter
@Setter
@NoArgsConstructor
public class LeaderboardChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatisticType category;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    public LeaderboardChannel(String guildId, StatisticType category, String channelId, String messageId) {
        this.guildId = guildId;
        this.category = category;
        this.channelId = channelId;
        this.messageId = messageId;
    }
}
