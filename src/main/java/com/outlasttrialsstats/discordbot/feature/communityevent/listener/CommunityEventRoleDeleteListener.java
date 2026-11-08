package com.outlasttrialsstats.discordbot.feature.communityevent.listener;

import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommunityEventRoleDeleteListener extends ListenerAdapter {

    private final CommunityEventService service;

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        service.clearRole(event.getGuild().getId(), event.getRole().getId());
    }
}
