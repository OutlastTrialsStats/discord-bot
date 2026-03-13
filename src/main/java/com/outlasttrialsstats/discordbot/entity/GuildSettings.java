package com.outlasttrialsstats.discordbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "guild_settings")
@Getter
@Setter
@NoArgsConstructor
public class GuildSettings {

    @Id
    @Column(name = "guild_id")
    private String guildId;

    @Column(nullable = false)
    private String language = "en";

    public GuildSettings(String guildId) {
        this.guildId = guildId;
    }
}
