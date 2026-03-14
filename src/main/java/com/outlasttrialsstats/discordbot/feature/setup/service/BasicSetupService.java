package com.outlasttrialsstats.discordbot.feature.setup.service;

import com.outlasttrialsstats.backend.api.model.AccountCreationType;
import com.outlasttrialsstats.backend.api.model.ActiveReagentSkillType;
import com.outlasttrialsstats.backend.api.model.InvasionRanking;
import com.outlasttrialsstats.backend.api.model.PlatformType;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import java.awt.Color;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BasicSetupService {

    private static final int[] PRESTIGE_THRESHOLDS = {1, 10, 20, 30, 40, 50, 60, 70, 80, 90};

    private static final Map<ActiveReagentSkillType, Color> SKILL_COLORS = Map.of(
            ActiveReagentSkillType.STUN, new Color(237, 17, 44),
            ActiveReagentSkillType.XRAY, new Color(3, 170, 13),
            ActiveReagentSkillType.MINE, new Color(116, 0, 198),
            ActiveReagentSkillType.DOOR_BLOCKER, new Color(216, 155, 0),
            ActiveReagentSkillType.HACKER, new Color(213, 85, 7),
            ActiveReagentSkillType.HEAL, new Color(7, 87, 165)
    );

    private static final Map<InvasionRanking, Color> INVASION_RANKING_COLORS = Map.ofEntries(
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

    private static final Map<PlatformType, Color> PLATFORM_COLORS = Map.of(
            PlatformType.STEAM, new Color(27, 40, 56),
            PlatformType.PLAYSTATION, new Color(0, 55, 145),
            PlatformType.XBOX, new Color(16, 124, 16),
            PlatformType.EPIC_GAMES, new Color(32, 32, 32)
    );

    private final RoleMappingService roleMappingService;

    public void setupRoles(Guild guild, Set<RoleCategory> categories,
                           BiConsumer<Integer, List<String>> onComplete, Runnable onNoChanges) {
        String guildId = guild.getId();
        List<Role> existingRoles = guild.getRoles();
        AtomicInteger pendingTasks = new AtomicInteger(0);
        List<String> createdRoles = new CopyOnWriteArrayList<>();

        if (categories.contains(RoleCategory.PRESTIGE)) {
            setupPrestigeRoles(guild, guildId, existingRoles, pendingTasks, createdRoles, onComplete);
        }
        if (categories.contains(RoleCategory.REAGENT_RIG)) {
            setupSkillRoles(guild, guildId, existingRoles, pendingTasks, createdRoles, onComplete);
        }
        if (categories.contains(RoleCategory.INVASION_RANKING)) {
            setupInvasionRankingRoles(guild, guildId, existingRoles, pendingTasks, createdRoles, onComplete);
        }
        if (categories.contains(RoleCategory.PLATFORM)) {
            setupPlatformRoles(guild, guildId, existingRoles, pendingTasks, createdRoles, onComplete);
        }
        if (categories.contains(RoleCategory.ACCOUNT_TYPE)) {
            setupAccountTypeRoles(guild, guildId, existingRoles, pendingTasks, createdRoles, onComplete);
        }

        if (pendingTasks.get() == 0) {
            onNoChanges.run();
        }
    }

    // --- Ranked role setup ---

    private void setupPrestigeRoles(Guild guild, String guildId, List<Role> existingRoles,
                                    AtomicInteger pendingTasks, List<String> createdRoles,
                                    BiConsumer<Integer, List<String>> onComplete) {
        for (int threshold : PRESTIGE_THRESHOLDS) {
            String roleName = "Prestige " + threshold + "+";
            Role existing = findRoleByName(existingRoles, roleName);

            if (existing != null) {
                roleMappingService.saveRankedMapping(guildId, RoleCategory.PRESTIGE, threshold, existing.getId());
            } else {
                pendingTasks.incrementAndGet();
                guild.createRole()
                        .setName(roleName)
                        .setHoisted(true)
                        .setPermissions(EnumSet.noneOf(Permission.class))
                        .queue(createdRole -> {
                            roleMappingService.saveRankedMapping(guildId, RoleCategory.PRESTIGE, threshold, createdRole.getId());
                            createdRoles.add(createdRole.getName());
                            if (pendingTasks.decrementAndGet() == 0) {
                                onComplete.accept(createdRoles.size(), createdRoles);
                            }
                        });
            }
        }
    }

    private void setupInvasionRankingRoles(Guild guild, String guildId, List<Role> existingRoles,
                                           AtomicInteger pendingTasks, List<String> createdRoles,
                                           BiConsumer<Integer, List<String>> onComplete) {
        for (InvasionRanking ranking : InvasionRanking.values()) {
            String roleName = formatInvasionRankingName(ranking);
            int rank = ranking.ordinal();
            Role existing = findRoleByName(existingRoles, roleName);

            if (existing != null) {
                roleMappingService.saveRankedMapping(guildId, RoleCategory.INVASION_RANKING, rank, existing.getId());
            } else {
                pendingTasks.incrementAndGet();
                guild.createRole()
                        .setName(roleName)
                        .setColor(INVASION_RANKING_COLORS.get(ranking))
                        .setPermissions(EnumSet.noneOf(Permission.class))
                        .queue(createdRole -> {
                            roleMappingService.saveRankedMapping(guildId, RoleCategory.INVASION_RANKING, rank, createdRole.getId());
                            createdRoles.add(createdRole.getName());
                            if (pendingTasks.decrementAndGet() == 0) {
                                onComplete.accept(createdRoles.size(), createdRoles);
                            }
                        });
            }
        }
    }

    // --- Enum role setup ---

    private void setupSkillRoles(Guild guild, String guildId, List<Role> existingRoles,
                                 AtomicInteger pendingTasks, List<String> createdRoles,
                                 BiConsumer<Integer, List<String>> onComplete) {
        for (ActiveReagentSkillType skill : ActiveReagentSkillType.values()) {
            String roleName = formatSkillName(skill);
            Role existing = findRoleByName(existingRoles, roleName);

            if (existing != null) {
                roleMappingService.saveEnumMapping(guildId, RoleCategory.REAGENT_RIG, skill.getValue(), existing.getId());
            } else {
                pendingTasks.incrementAndGet();
                guild.createRole()
                        .setName(roleName)
                        .setColor(SKILL_COLORS.get(skill))
                        .setPermissions(EnumSet.noneOf(Permission.class))
                        .queue(createdRole -> {
                            roleMappingService.saveEnumMapping(guildId, RoleCategory.REAGENT_RIG, skill.getValue(), createdRole.getId());
                            createdRoles.add(createdRole.getName());
                            if (pendingTasks.decrementAndGet() == 0) {
                                onComplete.accept(createdRoles.size(), createdRoles);
                            }
                        });
            }
        }
    }

    private void setupPlatformRoles(Guild guild, String guildId, List<Role> existingRoles,
                                    AtomicInteger pendingTasks, List<String> createdRoles,
                                    BiConsumer<Integer, List<String>> onComplete) {
        for (PlatformType platform : PlatformType.values()) {
            String roleName = formatPlatformName(platform);
            Role existing = findRoleByName(existingRoles, roleName);

            if (existing != null) {
                roleMappingService.saveEnumMapping(guildId, RoleCategory.PLATFORM, platform.getValue(), existing.getId());
            } else {
                pendingTasks.incrementAndGet();
                guild.createRole()
                        .setName(roleName)
                        .setColor(PLATFORM_COLORS.get(platform))
                        .setPermissions(EnumSet.noneOf(Permission.class))
                        .queue(createdRole -> {
                            roleMappingService.saveEnumMapping(guildId, RoleCategory.PLATFORM, platform.getValue(), createdRole.getId());
                            createdRoles.add(createdRole.getName());
                            if (pendingTasks.decrementAndGet() == 0) {
                                onComplete.accept(createdRoles.size(), createdRoles);
                            }
                        });
            }
        }
    }

    private void setupAccountTypeRoles(Guild guild, String guildId, List<Role> existingRoles,
                                       AtomicInteger pendingTasks, List<String> createdRoles,
                                       BiConsumer<Integer, List<String>> onComplete) {
        for (AccountCreationType accountType : AccountCreationType.values()) {
            String roleName = formatAccountTypeName(accountType);
            Role existing = findRoleByName(existingRoles, roleName);

            if (existing != null) {
                roleMappingService.saveEnumMapping(guildId, RoleCategory.ACCOUNT_TYPE, accountType.getValue(), existing.getId());
            } else {
                pendingTasks.incrementAndGet();
                guild.createRole()
                        .setName(roleName)
                        .setPermissions(EnumSet.noneOf(Permission.class))
                        .queue(createdRole -> {
                            roleMappingService.saveEnumMapping(guildId, RoleCategory.ACCOUNT_TYPE, accountType.getValue(), createdRole.getId());
                            createdRoles.add(createdRole.getName());
                            if (pendingTasks.decrementAndGet() == 0) {
                                onComplete.accept(createdRoles.size(), createdRoles);
                            }
                        });
            }
        }
    }

    // --- Helpers ---

    private Role findRoleByName(List<Role> roles, String name) {
        return roles.stream()
                .filter(r -> r.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public static String formatSkillName(ActiveReagentSkillType skill) {
        return switch (skill) {
            case STUN -> "Stun";
            case XRAY -> "X-Ray";
            case MINE -> "Mine";
            case DOOR_BLOCKER -> "Barricade";
            case HACKER -> "Jammer";
            case HEAL -> "Heal";
        };
    }

    public static String formatInvasionRankingName(InvasionRanking ranking) {
        return switch (ranking) {
            case UNRANKED -> "Invasion: Unranked";
            case INITIATE_3 -> "Invasion: Initiate III";
            case INITIATE_2 -> "Invasion: Initiate II";
            case INITIATE_1 -> "Invasion: Initiate I";
            case BRONZE_3 -> "Invasion: Bronze III";
            case BRONZE_2 -> "Invasion: Bronze II";
            case BRONZE_1 -> "Invasion: Bronze I";
            case SILVER_3 -> "Invasion: Silver III";
            case SILVER_2 -> "Invasion: Silver II";
            case SILVER_1 -> "Invasion: Silver I";
            case GOLD_3 -> "Invasion: Gold III";
            case GOLD_2 -> "Invasion: Gold II";
            case GOLD_1 -> "Invasion: Gold I";
        };
    }

    public static String formatPlatformName(PlatformType platform) {
        return switch (platform) {
            case STEAM -> "Steam";
            case PLAYSTATION -> "PlayStation";
            case XBOX -> "Xbox";
            case EPIC_GAMES -> "Epic Games";
        };
    }

    public static String formatAccountTypeName(AccountCreationType accountType) {
        return switch (accountType) {
            case CLOSED_BETA_USER -> "Closed Beta User";
            case EARLY_ACCESS_USER -> "Early Access User";
            case NORMAL_USER -> null;
        };
    }

    public static int invasionRankingToOrdinal(InvasionRanking ranking) {
        return ranking.ordinal();
    }
}
