package com.outlasttrialsstats.discordbot.feature.setup;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

/**
 * Decides whether an existing Discord role may be handed out automatically by the bot.
 * Linking a privileged role would let anyone who links a TotStats account escalate their
 * permissions, so roles that are public, managed, out of reach in the hierarchy or carry
 * moderation/administration permissions are rejected.
 */
public final class RoleSafety {

    public static final Set<Permission> DANGEROUS_PERMISSIONS = Collections.unmodifiableSet(EnumSet.of(
            Permission.ADMINISTRATOR,
            Permission.MANAGE_SERVER,
            Permission.MANAGE_ROLES,
            Permission.MANAGE_CHANNEL,
            Permission.MANAGE_PERMISSIONS,
            Permission.MANAGE_WEBHOOKS,
            Permission.MANAGE_GUILD_EXPRESSIONS,
            Permission.MANAGE_EVENTS,
            Permission.MANAGE_THREADS,
            Permission.VIEW_AUDIT_LOGS,
            Permission.BAN_MEMBERS,
            Permission.KICK_MEMBERS,
            Permission.MODERATE_MEMBERS,
            Permission.NICKNAME_MANAGE,
            Permission.MESSAGE_MANAGE,
            Permission.MESSAGE_MENTION_EVERYONE,
            Permission.VOICE_MUTE_OTHERS,
            Permission.VOICE_DEAF_OTHERS,
            Permission.VOICE_MOVE_OTHERS
    ));

    @Getter
    @RequiredArgsConstructor
    public enum Violation {
        PUBLIC_ROLE("error.role_unsafe.public"),
        MANAGED_ROLE("error.role_unsafe.managed"),
        BOT_CANNOT_INTERACT("error.role_unsafe.bot_hierarchy"),
        INVOKER_CANNOT_INTERACT("error.role_unsafe.invoker_hierarchy"),
        DANGEROUS_PERMISSIONS("error.role_unsafe.dangerous_permissions");

        private final String messageKey;
    }

    private RoleSafety() {
    }

    /**
     * Checks a role without considering who requested the link (e.g. for name-matched roles).
     */
    public static Optional<Violation> check(Role role, Member selfMember) {
        return check(role, selfMember, null);
    }

    /**
     * @param invoker the member who asked for the link; {@code null} skips the invoker hierarchy check
     */
    public static Optional<Violation> check(Role role, Member selfMember, Member invoker) {
        if (role.isPublicRole()) {
            return Optional.of(Violation.PUBLIC_ROLE);
        }
        if (role.isManaged()) {
            return Optional.of(Violation.MANAGED_ROLE);
        }
        if (!selfMember.canInteract(role)) {
            return Optional.of(Violation.BOT_CANNOT_INTERACT);
        }
        if (invoker != null && !invoker.canInteract(role)) {
            return Optional.of(Violation.INVOKER_CANNOT_INTERACT);
        }
        if (hasDangerousPermissions(role)) {
            return Optional.of(Violation.DANGEROUS_PERMISSIONS);
        }
        return Optional.empty();
    }

    public static boolean isSafe(Role role, Member selfMember) {
        return check(role, selfMember).isEmpty();
    }

    private static boolean hasDangerousPermissions(Role role) {
        return role.getPermissions().stream().anyMatch(DANGEROUS_PERMISSIONS::contains);
    }
}
