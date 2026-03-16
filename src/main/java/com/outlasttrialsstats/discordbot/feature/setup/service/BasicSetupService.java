package com.outlasttrialsstats.discordbot.feature.setup.service;

import com.outlasttrialsstats.backend.api.model.AccountCreationType;
import com.outlasttrialsstats.backend.api.model.ActiveReagentSkillType;
import com.outlasttrialsstats.backend.api.model.InvasionRanking;
import com.outlasttrialsstats.backend.api.model.PlatformType;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.RoleConfig;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class BasicSetupService {

    private final RoleMappingService roleMappingService;

    private record RoleDef(String name, Color color, boolean hoisted, BiConsumer<String, String> saveMapping) {
    }

    public void setupRoles(Guild guild, Set<RoleCategory> categories,
                           BiConsumer<Integer, List<String>> onComplete, Runnable onNoChanges) {
        String guildId = guild.getId();
        List<Role> existingRoles = guild.getRoles();
        AtomicInteger pendingTasks = new AtomicInteger(0);
        List<String> createdRoles = new CopyOnWriteArrayList<>();

        for (RoleCategory category : categories) {
            for (RoleDef def : buildRoleDefs(guildId, category).reversed()) {
                createOrLinkRole(guild, existingRoles, def, pendingTasks, createdRoles, onComplete);
            }
        }

        if (pendingTasks.get() == 0) {
            onNoChanges.run();
        }
    }

    private List<RoleDef> buildRoleDefs(String guildId, RoleCategory category) {
        return switch (category) {
            case PRESTIGE -> Arrays.stream(RoleConfig.PRESTIGE_THRESHOLDS)
                    .mapToObj(t -> new RoleDef(
                            "Prestige " + t + "+", null, true,
                            (_, roleId) -> roleMappingService.saveRankedMapping(guildId, RoleCategory.PRESTIGE, t, roleId)
                    )).toList();

            case LEVEL -> Arrays.stream(RoleConfig.LEVEL_THRESHOLDS)
                    .mapToObj(t -> new RoleDef(
                            "Level " + t + "+", null, false,
                            (_, roleId) -> roleMappingService.saveRankedMapping(guildId, RoleCategory.LEVEL, t, roleId)
                    )).toList();

            case INVASION_RANKING -> Arrays.stream(InvasionRanking.values())
                    .map(r -> new RoleDef(
                            RoleConfig.INVASION_RANKING_NAMES.get(r), RoleConfig.INVASION_RANKING_COLORS.get(r), false,
                            (_, roleId) -> roleMappingService.saveRankedMapping(guildId, RoleCategory.INVASION_RANKING, r.ordinal(), roleId)
                    )).toList();

            case TOTAL_INVASION_MATCHES -> Arrays.stream(RoleConfig.TOTAL_INVASION_MATCHES_THRESHOLDS)
                    .mapToObj(t -> new RoleDef(
                            "Invasion Matches " + t + "+", null, false,
                            (_, roleId) -> roleMappingService.saveRankedMapping(guildId, RoleCategory.TOTAL_INVASION_MATCHES, t, roleId)
                    )).toList();

            case REAGENT_RIG -> buildEnumRoleDefs(guildId, RoleCategory.REAGENT_RIG,
                    ActiveReagentSkillType.values(), RoleConfig.SKILL_NAMES::get, RoleConfig.SKILL_COLORS::get, ActiveReagentSkillType::getValue);

            case PLATFORM -> buildEnumRoleDefs(guildId, RoleCategory.PLATFORM,
                    PlatformType.values(), RoleConfig.PLATFORM_NAMES::get, RoleConfig.PLATFORM_COLORS::get, PlatformType::getValue);

            case ACCOUNT_TYPE -> buildEnumRoleDefs(guildId, RoleCategory.ACCOUNT_TYPE,
                    AccountCreationType.values(), RoleConfig.ACCOUNT_TYPE_NAMES::get, _ -> null, AccountCreationType::getValue);
        };
    }

    private <E> List<RoleDef> buildEnumRoleDefs(String guildId, RoleCategory category, E[] values,
                                                 Function<E, String> nameMapper, Function<E, Color> colorMapper,
                                                 Function<E, String> valueMapper) {
        return Arrays.stream(values)
                .filter(e -> nameMapper.apply(e) != null)
                .map(e -> new RoleDef(
                        nameMapper.apply(e), colorMapper.apply(e), false,
                        (_, roleId) -> roleMappingService.saveEnumMapping(guildId, category, valueMapper.apply(e), roleId)
                )).toList();
    }

    private void createOrLinkRole(Guild guild, List<Role> existingRoles, RoleDef def,
                                  AtomicInteger pendingTasks, List<String> createdRoles,
                                  BiConsumer<Integer, List<String>> onComplete) {
        Role existing = findRoleByName(existingRoles, def.name());
        if (existing != null) {
            def.saveMapping().accept(def.name(), existing.getId());
            return;
        }

        pendingTasks.incrementAndGet();
        var action = guild.createRole()
                .setName(def.name())
                .setHoisted(def.hoisted())
                .setPermissions(EnumSet.noneOf(Permission.class));

        if (def.color() != null) {
            action = action.setColor(def.color());
        }

        action.queue(createdRole -> {
            def.saveMapping().accept(def.name(), createdRole.getId());
            createdRoles.add(createdRole.getName());
            if (pendingTasks.decrementAndGet() == 0) {
                onComplete.accept(createdRoles.size(), createdRoles);
            }
        });
    }

    private Role findRoleByName(List<Role> roles, String name) {
        return roles.stream()
                .filter(r -> r.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}
