package com.outlasttrialsstats.discordbot.feature.setup;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoleCategory {

    PRESTIGE("prestige", "Prestige Roles"),
    LEVEL("level", "Level Roles"),
    REAGENT_RIG("reagent_rig", "Reagent Rig Roles"),
    INVASION_RANKING("invasion_ranking", "Invasion Ranking Roles"),
    TOTAL_INVASION_MATCHES("total_invasion_matches", "Total Invasion Matches Roles"),
    PLATFORM("platform", "Platform Roles"),
    ACCOUNT_TYPE("account_type", "Account Type Roles");

    private final String id;
    private final String displayName;

    public static RoleCategory fromId(String id) {
        for (RoleCategory category : values()) {
            if (category.id.equals(id)) {
                return category;
            }
        }
        return null;
    }
}
