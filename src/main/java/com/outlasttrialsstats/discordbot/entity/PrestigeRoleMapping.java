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
@Table(name = "prestige_role_mapping", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"guild_id", "min_prestige"})
})
@Getter
@Setter
@NoArgsConstructor
public class PrestigeRoleMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "min_prestige", nullable = false)
    private int minPrestige;

    @Column(name = "role_id", nullable = false)
    private String roleId;

    public PrestigeRoleMapping(String guildId, int minPrestige, String roleId) {
        this.guildId = guildId;
        this.minPrestige = minPrestige;
        this.roleId = roleId;
    }
}
