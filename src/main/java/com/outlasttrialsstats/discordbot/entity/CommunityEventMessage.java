package com.outlasttrialsstats.discordbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "community_event_message")
@Getter
@Setter
@NoArgsConstructor
public class CommunityEventMessage {

    @Id
    @Column(name = "guild_id")
    private String guildId;

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "shown_event_id", nullable = false)
    private String shownEventId;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    public CommunityEventMessage(String guildId, String eventId, String shownEventId, String channelId,
                                 String messageId) {
        this.guildId = guildId;
        this.eventId = eventId;
        this.shownEventId = shownEventId;
        this.channelId = channelId;
        this.messageId = messageId;
    }
}
