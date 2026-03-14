package com.outlasttrialsstats.discordbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "guild_server")
@Getter
@Setter
@NoArgsConstructor
public class GuildServer {

    @Id
    @Column(name = "guild_id")
    private String guildId;

    @Column(nullable = false)
    private String language = "en";

    @Column(name = "member_count", nullable = false)
    private int memberCount;

    public GuildServer(String guildId) {
        this.guildId = guildId;
    }
}
