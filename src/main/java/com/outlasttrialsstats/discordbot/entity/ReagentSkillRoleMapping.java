package com.outlasttrialsstats.discordbot.entity;

import com.outlasttrialsstats.backend.api.model.ActiveReagentSkillType;
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
@Table(name = "reagent_skill_role_mapping", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"guild_id", "skill"})
})
@Getter
@Setter
@NoArgsConstructor
public class ReagentSkillRoleMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActiveReagentSkillType skill;

    @Column(name = "role_id", nullable = false)
    private String roleId;

    public ReagentSkillRoleMapping(String guildId, ActiveReagentSkillType skill, String roleId) {
        this.guildId = guildId;
        this.skill = skill;
        this.roleId = roleId;
    }
}
