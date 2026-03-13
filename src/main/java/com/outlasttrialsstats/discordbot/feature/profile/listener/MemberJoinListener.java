package com.outlasttrialsstats.discordbot.feature.profile.listener;

import com.outlasttrialsstats.discordbot.feature.profile.service.RoleAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemberJoinListener extends ListenerAdapter {

    private final RoleAssignmentService roleAssignmentService;

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        var member = event.getMember();
        if (member.getUser().isBot()) {
            return;
        }

        log.info("Member {} joined guild {}", member.getId(), event.getGuild().getId());
        var result = roleAssignmentService.assignRoles(event.getGuild(), member);

        if (result.verified()) {
            log.info("Assigned roles to verified member {}: added={}, removed={}",
                    member.getId(), result.addedRoles(), result.removedRoles());
        } else {
            log.debug("Member {} is not verified, skipping role assignment", member.getId());
        }
    }
}
