package com.outlasttrialsstats.discordbot.feature.setup;

import com.outlasttrialsstats.backend.api.model.AccountCreationType;
import com.outlasttrialsstats.backend.api.model.ActiveReagentSkillType;
import com.outlasttrialsstats.backend.api.model.InvasionRanking;
import com.outlasttrialsstats.backend.api.model.PlatformType;
import java.awt.Color;
import java.util.Map;

public final class RoleConfig {

    private RoleConfig() {}

    public static final int[] PRESTIGE_THRESHOLDS = {1, 10, 20, 30, 40, 50, 60, 70, 80, 90};

    public static final Map<ActiveReagentSkillType, String> SKILL_NAMES = Map.of(
            ActiveReagentSkillType.STUN, "Stun",
            ActiveReagentSkillType.XRAY, "X-Ray",
            ActiveReagentSkillType.MINE, "Mine",
            ActiveReagentSkillType.DOOR_BLOCKER, "Barricade",
            ActiveReagentSkillType.HACKER, "Jammer",
            ActiveReagentSkillType.HEAL, "Heal"
    );

    public static final Map<ActiveReagentSkillType, Color> SKILL_COLORS = Map.of(
            ActiveReagentSkillType.STUN, new Color(237, 17, 44),
            ActiveReagentSkillType.XRAY, new Color(3, 170, 13),
            ActiveReagentSkillType.MINE, new Color(116, 0, 198),
            ActiveReagentSkillType.DOOR_BLOCKER, new Color(216, 155, 0),
            ActiveReagentSkillType.HACKER, new Color(213, 85, 7),
            ActiveReagentSkillType.HEAL, new Color(7, 87, 165)
    );

    public static final Map<InvasionRanking, String> INVASION_RANKING_NAMES = Map.ofEntries(
            Map.entry(InvasionRanking.UNRANKED, "Invasion: Unranked"),
            Map.entry(InvasionRanking.INITIATE_3, "Invasion: Initiate III"),
            Map.entry(InvasionRanking.INITIATE_2, "Invasion: Initiate II"),
            Map.entry(InvasionRanking.INITIATE_1, "Invasion: Initiate I"),
            Map.entry(InvasionRanking.BRONZE_3, "Invasion: Bronze III"),
            Map.entry(InvasionRanking.BRONZE_2, "Invasion: Bronze II"),
            Map.entry(InvasionRanking.BRONZE_1, "Invasion: Bronze I"),
            Map.entry(InvasionRanking.SILVER_3, "Invasion: Silver III"),
            Map.entry(InvasionRanking.SILVER_2, "Invasion: Silver II"),
            Map.entry(InvasionRanking.SILVER_1, "Invasion: Silver I"),
            Map.entry(InvasionRanking.GOLD_3, "Invasion: Gold III"),
            Map.entry(InvasionRanking.GOLD_2, "Invasion: Gold II"),
            Map.entry(InvasionRanking.GOLD_1, "Invasion: Gold I")
    );

    public static final Map<InvasionRanking, Color> INVASION_RANKING_COLORS = Map.ofEntries(
            Map.entry(InvasionRanking.UNRANKED, new Color(128, 128, 128)),
            Map.entry(InvasionRanking.INITIATE_3, new Color(173, 216, 230)),
            Map.entry(InvasionRanking.INITIATE_2, new Color(135, 206, 235)),
            Map.entry(InvasionRanking.INITIATE_1, new Color(100, 149, 237)),
            Map.entry(InvasionRanking.BRONZE_3, new Color(205, 127, 50)),
            Map.entry(InvasionRanking.BRONZE_2, new Color(184, 115, 51)),
            Map.entry(InvasionRanking.BRONZE_1, new Color(166, 100, 40)),
            Map.entry(InvasionRanking.SILVER_3, new Color(192, 192, 192)),
            Map.entry(InvasionRanking.SILVER_2, new Color(169, 169, 169)),
            Map.entry(InvasionRanking.SILVER_1, new Color(150, 150, 150)),
            Map.entry(InvasionRanking.GOLD_3, new Color(255, 215, 0)),
            Map.entry(InvasionRanking.GOLD_2, new Color(218, 165, 32)),
            Map.entry(InvasionRanking.GOLD_1, new Color(184, 134, 11))
    );

    public static final Map<PlatformType, String> PLATFORM_NAMES = Map.of(
            PlatformType.STEAM, "Steam",
            PlatformType.PLAYSTATION, "PlayStation",
            PlatformType.XBOX, "Xbox",
            PlatformType.EPIC_GAMES, "Epic Games"
    );

    public static final Map<PlatformType, Color> PLATFORM_COLORS = Map.of(
            PlatformType.STEAM, new Color(27, 40, 56),
            PlatformType.PLAYSTATION, new Color(0, 55, 145),
            PlatformType.XBOX, new Color(16, 124, 16),
            PlatformType.EPIC_GAMES, new Color(32, 32, 32)
    );

    public static final Map<AccountCreationType, String> ACCOUNT_TYPE_NAMES = Map.of(
            AccountCreationType.CLOSED_BETA_USER, "Closed Beta User",
            AccountCreationType.EARLY_ACCESS_USER, "Early Access User"
    );

    public static String skillName(ActiveReagentSkillType skill) {
        return SKILL_NAMES.get(skill);
    }

    public static int invasionRankingOrdinal(InvasionRanking ranking) {
        return ranking.ordinal();
    }
}
