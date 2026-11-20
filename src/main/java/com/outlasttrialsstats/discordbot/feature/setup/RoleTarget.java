package com.outlasttrialsstats.discordbot.feature.setup;

import com.outlasttrialsstats.backend.api.model.AccountCreationType;
import com.outlasttrialsstats.backend.api.model.ActiveReagentSkillType;
import com.outlasttrialsstats.backend.api.model.InvasionRanking;
import com.outlasttrialsstats.backend.api.model.PlatformType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public record RoleTarget(RoleCategory category, String key, String label, Integer rank, String enumValue,
                         String successKey, Object successArg, String removedKey, List<Object> removedArgs) {

    public boolean ranked() {
        return rank != null;
    }

    public static boolean usesThreshold(RoleCategory category) {
        return switch (category) {
            case PRESTIGE, LEVEL, TOTAL_INVASION_MATCHES, SEASON_INVASION_POINTS -> true;
            default -> false;
        };
    }

    public static List<RoleTarget> choices(RoleCategory category) {
        return switch (category) {
            case REAGENT_RIG -> Arrays.stream(ActiveReagentSkillType.values())
                    .filter(RoleConfig.SKILL_NAMES::containsKey).map(RoleTarget::skill).toList();
            case INVASION_RANKING -> Arrays.stream(InvasionRanking.values())
                    .filter(RoleConfig.INVASION_RANKING_NAMES::containsKey).map(RoleTarget::invasionRanking).toList();
            case PLATFORM -> Arrays.stream(PlatformType.values())
                    .filter(RoleConfig.PLATFORM_NAMES::containsKey).map(RoleTarget::platform).toList();
            case ACCOUNT_TYPE -> Arrays.stream(AccountCreationType.values())
                    .filter(RoleConfig.ACCOUNT_TYPE_NAMES::containsKey).map(RoleTarget::accountType).toList();
            case CONNECTED_ACCOUNT -> List.of(connectedAccount());
            default -> List.of();
        };
    }

    public static Optional<RoleTarget> parse(RoleCategory category, String key) {
        if (key == null) {
            return Optional.empty();
        }
        if (usesThreshold(category)) {
            try {
                int threshold = Integer.parseInt(key.trim());
                return threshold < 1 ? Optional.empty() : Optional.of(threshold(category, threshold));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return choices(category).stream().filter(target -> target.key().equals(key)).findFirst();
    }

    public static Optional<RoleTarget> fromRanked(RoleCategory category, int rank) {
        if (usesThreshold(category)) {
            return Optional.of(threshold(category, rank));
        }
        return choices(category).stream().filter(target -> target.ranked() && target.rank() == rank).findFirst();
    }

    public static Optional<RoleTarget> fromEnum(RoleCategory category, String enumValue) {
        return choices(category).stream().filter(target -> enumValue.equals(target.enumValue())).findFirst();
    }

    static RoleTarget threshold(RoleCategory category, int threshold) {
        String key = String.valueOf(threshold);
        return switch (category) {
            case PRESTIGE -> new RoleTarget(category, key, "Prestige " + threshold + "+", threshold, null,
                    "setup.prestige_role.success", threshold, "setup.prestige_role.removed", List.of(threshold));
            case LEVEL -> new RoleTarget(category, key, "Level " + threshold + "+", threshold, null,
                    "setup.level_role.success", threshold, "setup.level_role.removed", List.of(threshold));
            case TOTAL_INVASION_MATCHES -> new RoleTarget(category, key, "Invasion Matches " + threshold + "+",
                    threshold, null, "setup.total_invasion_matches_role.success", threshold,
                    "setup.total_invasion_matches_role.removed", List.of(threshold));
            case SEASON_INVASION_POINTS -> new RoleTarget(category, key, "Invasion Points " + threshold + "+",
                    threshold, null, "setup.season_invasion_points_role.success", threshold,
                    "setup.season_invasion_points_role.removed", List.of(threshold));
            default -> throw new IllegalArgumentException("No threshold category: " + category);
        };
    }

    private static RoleTarget skill(ActiveReagentSkillType skill) {
        String name = RoleConfig.skillName(skill);
        return new RoleTarget(RoleCategory.REAGENT_RIG, skill.getValue(), name, null, skill.getValue(),
                "setup.skill_role.success", name, "setup.skill_role.removed", List.of(name));
    }

    private static RoleTarget invasionRanking(InvasionRanking ranking) {
        String name = RoleConfig.INVASION_RANKING_NAMES.get(ranking);
        return new RoleTarget(RoleCategory.INVASION_RANKING, ranking.getValue(), name, ranking.ordinal(), null,
                "setup.role_mapping.success", name, "setup.role_mapping.removed", List.of(name));
    }

    private static RoleTarget platform(PlatformType platform) {
        String name = RoleConfig.PLATFORM_NAMES.get(platform);
        return new RoleTarget(RoleCategory.PLATFORM, platform.getValue(), name, null, platform.getValue(),
                "setup.role_mapping.success", name, "setup.role_mapping.removed", List.of(name));
    }

    private static RoleTarget accountType(AccountCreationType type) {
        String name = RoleConfig.ACCOUNT_TYPE_NAMES.get(type);
        return new RoleTarget(RoleCategory.ACCOUNT_TYPE, type.getValue(), name, null, type.getValue(),
                "setup.role_mapping.success", name, "setup.role_mapping.removed", List.of(name));
    }

    private static RoleTarget connectedAccount() {
        return new RoleTarget(RoleCategory.CONNECTED_ACCOUNT, RoleConfig.CONNECTED_ACCOUNT_VALUE,
                "Account Connected", null, RoleConfig.CONNECTED_ACCOUNT_VALUE,
                "setup.connected_account_role.success", "Account Connected",
                "setup.connected_account_role.removed", List.of());
    }
}
