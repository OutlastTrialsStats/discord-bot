package com.outlasttrialsstats.discordbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "community_event_alert_config")
@Getter
@Setter
@NoArgsConstructor
public class CommunityEventAlertConfig {

    @Id
    @Column(name = "guild_id")
    private String guildId;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    public CommunityEventAlertConfig(String guildId, String channelId) {
        this.guildId = guildId;
        this.channelId = channelId;
    }
}
