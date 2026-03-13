package com.outlasttrialsstats.discordbot.feature.setup.service;

import com.outlasttrialsstats.backend.api.model.ActiveReagentSkillType;
import java.awt.Color;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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

    private final RoleMappingService roleMappingService;

    public void setupAllRoles(Guild guild, BiConsumer<Integer, List<String>> onComplete, Runnable onNoChanges) {
        String guildId = guild.getId();
        List<Role> existingRoles = guild.getRoles();
        AtomicInteger pendingTasks = new AtomicInteger(0);
        List<String> createdRoles = new CopyOnWriteArrayList<>();

        setupPrestigeRoles(guild, guildId, existingRoles, pendingTasks, createdRoles, onComplete);
        setupSkillRoles(guild, guildId, existingRoles, pendingTasks, createdRoles, onComplete);

        if (pendingTasks.get() == 0) {
            onNoChanges.run();
        }
    }

    private void setupPrestigeRoles(Guild guild, String guildId, List<Role> existingRoles,
                                    AtomicInteger pendingTasks, List<String> createdRoles,
                                    BiConsumer<Integer, List<String>> onComplete) {
        for (int threshold : PRESTIGE_THRESHOLDS) {
            String roleName = "Prestige " + threshold + "+";
            Role existing = findRoleByName(existingRoles, roleName);

            if (existing != null) {
                roleMappingService.savePrestigeMapping(guildId, threshold, existing.getId());
            } else {
                pendingTasks.incrementAndGet();
                guild.createRole()
                        .setName(roleName)
                        .setHoisted(true)
                        .setPermissions(EnumSet.noneOf(Permission.class))
                        .queue(createdRole -> {
                            roleMappingService.savePrestigeMapping(guildId, threshold, createdRole.getId());
                            createdRoles.add(createdRole.getName());
                            if (pendingTasks.decrementAndGet() == 0) {
                                onComplete.accept(createdRoles.size(), createdRoles);
                            }
                        });
            }
        }
    }

    private void setupSkillRoles(Guild guild, String guildId, List<Role> existingRoles,
                                 AtomicInteger pendingTasks, List<String> createdRoles,
                                 BiConsumer<Integer, List<String>> onComplete) {
        for (ActiveReagentSkillType skill : ActiveReagentSkillType.values()) {
            String roleName = formatSkillName(skill);
            Role existing = findRoleByName(existingRoles, roleName);

            if (existing != null) {
                roleMappingService.saveSkillMapping(guildId, skill, existing.getId());
            } else {
                pendingTasks.incrementAndGet();
                guild.createRole()
                        .setName(roleName)
                        .setColor(SKILL_COLORS.get(skill))
                        .setPermissions(EnumSet.noneOf(Permission.class))
                        .queue(createdRole -> {
                            roleMappingService.saveSkillMapping(guildId, skill, createdRole.getId());
                            createdRoles.add(createdRole.getName());
                            if (pendingTasks.decrementAndGet() == 0) {
                                onComplete.accept(createdRoles.size(), createdRoles);
                            }
                        });
            }
        }
    }

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
}
