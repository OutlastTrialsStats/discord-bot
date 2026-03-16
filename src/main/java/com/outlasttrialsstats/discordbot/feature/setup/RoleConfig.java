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
    public static final int[] LEVEL_THRESHOLDS = {1, 10, 25, 50, 75, 99 };
    public static final int[] TOTAL_INVASION_MATCHES_THRESHOLDS = {1, 50, 100, 250, 500, 1000};

    public static final Map<ActiveReagentSkillType, String> SKILL_NAMES = Map.of(
            ActiveReagentSkillType.STUN, "Stun",
            ActiveReagentSkillType.XRAY, "X-Ray",
            ActiveReagentSkillType.MINE, "Mine",
            ActiveReagentSkillType.DOOR_BLOCKER, "Barricade",
            ActiveReagentSkillType.HACKER, "Jammer",
            ActiveReagentSkillType.HEAL, "Heal"
    );

    public static final Map<ActiveReagentSkillType, Color> SKILL_COLORS = Map.of(
            ActiveReagentSkillType.STUN, new Color(159, 71, 58),
            ActiveReagentSkillType.XRAY, new Color(85, 104, 69),
            ActiveReagentSkillType.MINE, new Color(125, 95, 136),
            ActiveReagentSkillType.DOOR_BLOCKER, new Color(200, 182, 79),
            ActiveReagentSkillType.HACKER, new Color(213, 85, 7),
            ActiveReagentSkillType.HEAL, new Color(108, 163, 158)
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
            Map.entry(InvasionRanking.UNRANKED, new Color(83, 82, 83)),
            Map.entry(InvasionRanking.INITIATE_3, new Color(118, 105, 100)),
            Map.entry(InvasionRanking.INITIATE_2, new Color(118, 105, 100)),
            Map.entry(InvasionRanking.INITIATE_1, new Color(118, 105, 100)),
            Map.entry(InvasionRanking.BRONZE_3, new Color(120, 91, 71)),
            Map.entry(InvasionRanking.BRONZE_2, new Color(120, 91, 71)),
            Map.entry(InvasionRanking.BRONZE_1, new Color(120, 91, 71)),
            Map.entry(InvasionRanking.SILVER_3, new Color(184, 184, 184)),
            Map.entry(InvasionRanking.SILVER_2, new Color(184, 184, 184)),
            Map.entry(InvasionRanking.SILVER_1, new Color(184, 184, 184)),
            Map.entry(InvasionRanking.GOLD_3, new Color(180, 130, 34)),
            Map.entry(InvasionRanking.GOLD_2, new Color(180, 130, 34)),
            Map.entry(InvasionRanking.GOLD_1, new Color(180, 130, 34))
    );

    public static final Map<PlatformType, String> PLATFORM_NAMES = Map.of(
            PlatformType.STEAM, "Steam",
            PlatformType.PLAYSTATION, "PlayStation",
            PlatformType.XBOX, "Xbox",
            PlatformType.EPIC_GAMES, "Epic Games"
    );

    public static final Map<PlatformType, Color> PLATFORM_COLORS = Map.of(
            PlatformType.STEAM, new Color(41,46,55),
            PlatformType.PLAYSTATION, new Color(0, 112, 204),
            PlatformType.XBOX, new Color(12, 122, 31),
            PlatformType.EPIC_GAMES, new Color(47, 45, 46)
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
