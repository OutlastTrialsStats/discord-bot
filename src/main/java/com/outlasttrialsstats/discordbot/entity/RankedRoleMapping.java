package com.outlasttrialsstats.discordbot.entity;

import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
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
@Table(name = "ranked_role_mapping", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"guild_id", "category", "min_rank"})
})
@Getter
@Setter
@NoArgsConstructor
public class RankedRoleMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleCategory category;

    @Column(name = "min_rank", nullable = false)
    private int minRank;

    @Column(name = "role_id", nullable = false)
    private String roleId;

    public RankedRoleMapping(String guildId, RoleCategory category, int minRank, String roleId) {
        this.guildId = guildId;
        this.category = category;
        this.minRank = minRank;
        this.roleId = roleId;
    }
}
