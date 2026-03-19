package com.outlasttrialsstats.discordbot.feature.profile.command;

import com.outlasttrialsstats.discordbot.feature.profile.dto.RoleAssignmentResult;
import com.outlasttrialsstats.discordbot.feature.profile.service.RoleAssignmentService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileCommand {

    private final RoleAssignmentService roleAssignmentService;
    private final MessageService messageService;

    public void onProfileUpdate(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();

        RoleAssignmentResult result = roleAssignmentService.assignRoles(guild, event.getMember());

        if (!result.verified()) {
            event.reply(messageService.getMessage(guildId, "profile.not_verified"))
                    .setEphemeral(true).queue();
            return;
        }

        if (result.hasChanges()) {
            String added = result.addedRoles().isEmpty() ? "-" : String.join(", ", result.addedRoles());
            String removed = result.removedRoles().isEmpty() ? "-" : String.join(", ", result.removedRoles());
            event.reply(messageService.getMessage(guildId, "profile.updated", added, removed))
                    .setEphemeral(true).queue();
        } else {
            event.reply(messageService.getMessage(guildId, "profile.no_changes"))
                    .setEphemeral(true).queue();
        }
    }
}
