package com.outlasttrialsstats.discordbot.feature.setup.service;

import com.outlasttrialsstats.backend.api.model.AccountCreationType;
import com.outlasttrialsstats.backend.api.model.ActiveReagentSkillType;
import com.outlasttrialsstats.backend.api.model.InvasionRanking;
import com.outlasttrialsstats.backend.api.model.PlatformType;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.RoleConfig;
import com.outlasttrialsstats.discordbot.feature.setup.RoleSafety;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
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
import java.util.function.Consumer;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class BasicSetupService {

    private final RoleMappingService roleMappingService;

    private record RoleDef(String name, Color color, boolean hoisted, BiConsumer<String, Boolean> saveMapping) {
    }

    /**
     * Outcome of a basic setup run: the names of the roles that were created and of those that could not be
     * created (or whose mapping could not be saved).
     */
    public record SetupResult(List<String> createdRoles, List<String> failedRoles) {
    }

    /**
     * Creates or links the roles of the given categories. Exactly one of the callbacks is invoked once all
     * role creations have finished: {@code onComplete} if any role was created or failed, otherwise
     * {@code onNoChanges}.
     */
    public void setupRoles(Guild guild, Set<RoleCategory> categories,
                           Consumer<SetupResult> onComplete, Runnable onNoChanges) {
        String guildId = guild.getId();
        List<Role> existingRoles = guild.getRoles();
        // Starts at 1 as a sentinel for the loop itself, so that creations completing while the loop is still
        // queueing further ones cannot drop the counter to 0 and finish early.
        AtomicInteger pendingTasks = new AtomicInteger(1);
        List<String> createdRoles = new CopyOnWriteArrayList<>();
        List<String> failedRoles = new CopyOnWriteArrayList<>();
        Runnable taskDone = () -> {
            if (pendingTasks.decrementAndGet() == 0) {
                if (createdRoles.isEmpty() && failedRoles.isEmpty()) {
                    onNoChanges.run();
                } else {
                    onComplete.accept(new SetupResult(List.copyOf(createdRoles), List.copyOf(failedRoles)));
                }
            }
        };

        try {
            for (RoleCategory category : categories) {
                for (RoleDef def : buildRoleDefs(guildId, category).reversed()) {
                    createOrLinkRole(guild, existingRoles, def, pendingTasks, createdRoles, failedRoles, taskDone);
                }
            }
        } finally {
            taskDone.run();
        }
    }

    private List<RoleDef> buildRoleDefs(String guildId, RoleCategory category) {
        return switch (category) {
            case PRESTIGE -> Arrays.stream(RoleConfig.PRESTIGE_THRESHOLDS)
                    .mapToObj(t -> new RoleDef(
                            "Prestige " + t + "+", null, true,
                            (roleId, botCreated) -> roleMappingService.saveRankedMapping(guildId, RoleCategory.PRESTIGE, t, roleId, botCreated)
                    )).toList();

            case LEVEL -> Arrays.stream(RoleConfig.LEVEL_THRESHOLDS)
                    .mapToObj(t -> new RoleDef(
                            "Level " + t + "+", null, false,
                            (roleId, botCreated) -> roleMappingService.saveRankedMapping(guildId, RoleCategory.LEVEL, t, roleId, botCreated)
                    )).toList();

            case INVASION_RANKING -> Arrays.stream(InvasionRanking.values())
                    .map(r -> new RoleDef(
                            RoleConfig.INVASION_RANKING_NAMES.get(r), RoleConfig.INVASION_RANKING_COLORS.get(r), false,
                            (roleId, botCreated) -> roleMappingService.saveRankedMapping(guildId, RoleCategory.INVASION_RANKING, r.ordinal(), roleId, botCreated)
                    )).toList();

            case TOTAL_INVASION_MATCHES -> Arrays.stream(RoleConfig.TOTAL_INVASION_MATCHES_THRESHOLDS)
                    .mapToObj(t -> new RoleDef(
                            "Invasion Matches " + t + "+", null, false,
                            (roleId, botCreated) -> roleMappingService.saveRankedMapping(guildId, RoleCategory.TOTAL_INVASION_MATCHES, t, roleId, botCreated)
                    )).toList();

            case SEASON_INVASION_POINTS -> Arrays.stream(RoleConfig.SEASON_INVASION_POINTS_THRESHOLDS)
                    .mapToObj(t -> new RoleDef(
                            "Invasion Points " + t + "+", null, false,
                            (roleId, botCreated) -> roleMappingService.saveRankedMapping(guildId, RoleCategory.SEASON_INVASION_POINTS, t, roleId, botCreated)
                    )).toList();

            case REAGENT_RIG -> buildEnumRoleDefs(guildId, RoleCategory.REAGENT_RIG,
                    ActiveReagentSkillType.values(), RoleConfig.SKILL_NAMES::get, RoleConfig.SKILL_COLORS::get, ActiveReagentSkillType::getValue);

            case PLATFORM -> buildEnumRoleDefs(guildId, RoleCategory.PLATFORM,
                    PlatformType.values(), RoleConfig.PLATFORM_NAMES::get, RoleConfig.PLATFORM_COLORS::get, PlatformType::getValue);

            case ACCOUNT_TYPE -> buildEnumRoleDefs(guildId, RoleCategory.ACCOUNT_TYPE,
                    AccountCreationType.values(), RoleConfig.ACCOUNT_TYPE_NAMES::get, _ -> null, AccountCreationType::getValue);

            case CONNECTED_ACCOUNT -> List.of(new RoleDef(
                    "Account Connected", null, false,
                    (roleId, botCreated) -> roleMappingService.saveEnumMapping(
                            guildId, RoleCategory.CONNECTED_ACCOUNT,
                            RoleConfig.CONNECTED_ACCOUNT_VALUE, roleId, botCreated)
            ));
        };
    }

    private <E> List<RoleDef> buildEnumRoleDefs(String guildId, RoleCategory category, E[] values,
                                                 Function<E, String> nameMapper, Function<E, Color> colorMapper,
                                                 Function<E, String> valueMapper) {
        return Arrays.stream(values)
                .filter(e -> nameMapper.apply(e) != null)
                .map(e -> new RoleDef(
                        nameMapper.apply(e), colorMapper.apply(e), false,
                        (roleId, botCreated) -> roleMappingService.saveEnumMapping(guildId, category, valueMapper.apply(e), roleId, botCreated)
                )).toList();
    }

    private void createOrLinkRole(Guild guild, List<Role> existingRoles, RoleDef def,
                                  AtomicInteger pendingTasks, List<String> createdRoles, List<String> failedRoles,
                                  Runnable taskDone) {
        Role existing = findLinkableRoleByName(guild, existingRoles, def.name());
        if (existing != null) {
            try {
                def.saveMapping().accept(existing.getId(), false);
            } catch (Exception e) {
                // One failing mapping must not abort the remaining categories of the setup
                log.error("Failed to save mapping for existing role {} in guild {}",
                        existing.getName(), guild.getId(), e);
                failedRoles.add(def.name());
            }
            return;
        }

        pendingTasks.incrementAndGet();
        try {
            var action = guild.createRole()
                    .setName(def.name())
                    .setHoisted(def.hoisted())
                    .setPermissions(EnumSet.noneOf(Permission.class));

            if (def.color() != null) {
                action = action.setColor(def.color());
            }

            action.queue(createdRole -> {
                try {
                    def.saveMapping().accept(createdRole.getId(), true);
                    createdRoles.add(createdRole.getName());
                } catch (Exception e) {
                    log.error("Failed to save mapping for created role {} in guild {}",
                            createdRole.getName(), guild.getId(), e);
                    failedRoles.add(def.name());
                } finally {
                    taskDone.run();
                }
            }, error -> {
                log.warn("Failed to create role {} in guild {}: {}", def.name(), guild.getId(), error.getMessage());
                failedRoles.add(def.name());
                taskDone.run();
            });
        } catch (Exception e) {
            log.warn("Failed to create role {} in guild {}: {}", def.name(), guild.getId(), e.getMessage());
            failedRoles.add(def.name());
            taskDone.run();
        }
    }

    /**
     * Finds an existing role with the given name that is safe to hand out. A name-matched role that is
     * privileged or out of the bot's reach is ignored so that a fresh, permission-less role gets created.
     */
    private Role findLinkableRoleByName(Guild guild, List<Role> roles, String name) {
        Member selfMember = guild.getSelfMember();
        return roles.stream()
                .filter(r -> r.getName().equalsIgnoreCase(name))
                .filter(r -> RoleSafety.isSafe(r, selfMember))
                .findFirst()
                .orElse(null);
    }
}
