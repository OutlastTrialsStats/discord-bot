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
@Table(name = "guild_messages", uniqueConstraints = @UniqueConstraint(columnNames = {"guild_id", "message_key"}))
@Getter
@Setter
@NoArgsConstructor
public class GuildMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @Column(name = "message_value", nullable = false, length = 2000)
    private String messageValue;

    public GuildMessage(String guildId, String messageKey, String messageValue) {
        this.guildId = guildId;
        this.messageKey = messageKey;
        this.messageValue = messageValue;
    }
}
