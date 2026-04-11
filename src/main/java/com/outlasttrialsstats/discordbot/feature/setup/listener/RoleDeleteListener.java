package com.outlasttrialsstats.discordbot.feature.setup.listener;

import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoleDeleteListener extends ListenerAdapter {

    private final RoleMappingService roleMappingService;

    @Override
    @Transactional
    public void onRoleDelete(RoleDeleteEvent event) {
        String guildId = event.getGuild().getId();
        String roleId = event.getRole().getId();
        String roleName = event.getRole().getName();

        roleMappingService.deleteMappingsByRoleId(guildId, roleId);
        log.info("Role '{}' ({}) deleted in guild {}, removed stale mappings", roleName, roleId, guildId);
    }
}
