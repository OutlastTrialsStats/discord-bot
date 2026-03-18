package com.outlasttrialsstats.discordbot.feature.profile.command;

import com.outlasttrialsstats.discordbot.feature.profile.dto.RoleAssignmentResult;
import com.outlasttrialsstats.discordbot.feature.profile.service.RoleAssignmentService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import io.github.kaktushose.jdac.annotations.interactions.Command;
import io.github.kaktushose.jdac.annotations.interactions.Interaction;
import io.github.kaktushose.jdac.dispatching.events.interactions.CommandEvent;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.stereotype.Component;

@Interaction
@Component
@RequiredArgsConstructor
public class ProfileCommand {

    private final RoleAssignmentService roleAssignmentService;
    private final MessageService messageService;

    @Command(value = "sync-profile", desc = "Sync your roles based on your Outlast Trials stats")
    public void onProfileUpdate(CommandEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();

        RoleAssignmentResult result = roleAssignmentService.assignRoles(guild, event.getMember());

        if (!result.verified()) {
            event.with().ephemeral(true)
                    .reply(messageService.getMessage(guildId, "profile.not_verified"));
            return;
        }

        if (result.hasChanges()) {
            String added = result.addedRoles().isEmpty() ? "-" : String.join(", ", result.addedRoles());
            String removed = result.removedRoles().isEmpty() ? "-" : String.join(", ", result.removedRoles());
            event.with().ephemeral(true)
                    .reply(messageService.getMessage(guildId, "profile.updated", added, removed));
        } else {
            event.with().ephemeral(true)
                    .reply(messageService.getMessage(guildId, "profile.no_changes"));
        }
    }
}
