package com.outlasttrialsstats.discordbot.feature.profile.command;

import com.outlasttrialsstats.discordbot.feature.profile.dto.RoleAssignmentResult;
import com.outlasttrialsstats.discordbot.feature.profile.service.RoleAssignmentService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileCommand {

    private final RoleAssignmentService roleAssignmentService;
    private final MessageService messageService;

    public void onProfileUpdate(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);

        event.deferReply(true).queue();
        var hook = event.getHook();

        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            hook.editOriginal(messageService.getMessage(guildId, "error.missing_permission.manage_roles")).queue();
            return;
        }

        RoleAssignmentResult result = roleAssignmentService.assignRoles(guild, event.getMember());

        if (result.isLookupFailed()) {
            hook.editOriginal(messageService.getMessage(guildId, "profile.backend_unavailable")).queue();
            return;
        }

        if (!result.verified()) {
            hook.editOriginal(messageService.getMessage(guildId, "profile.not_verified")).queue();
            return;
        }

        if (result.hasChanges()) {
            String added = result.addedRoles().isEmpty() ? "-" : String.join(", ", result.addedRoles());
            String removed = result.removedRoles().isEmpty() ? "-" : String.join(", ", result.removedRoles());
            hook.editOriginal(messageService.getMessage(guildId, "profile.updated", added, removed)).queue();
        } else {
            hook.editOriginal(messageService.getMessage(guildId, "profile.no_changes")).queue();
        }
    }
}
