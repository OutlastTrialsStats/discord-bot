package com.outlasttrialsstats.discordbot.feature.profile.service;

import com.outlasttrialsstats.discordbot.feature.profile.dto.GuildSyncResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuildSyncService {

    private final RoleAssignmentService roleAssignmentService;

    public GuildSyncResult syncMembers(Guild guild, List<Member> members) {
        int updated = 0;
        int skipped = 0;

        for (var member : members) {
            if (member.getUser().isBot()) {
                continue;
            }

            try {
                var result = roleAssignmentService.assignRoles(guild, member);
                if (result.verified() && result.hasChanges()) {
                    updated++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.warn("Failed to sync roles for member {} in guild {}: {}",
                        member.getId(), guild.getId(), e.getMessage());
                skipped++;
            }
        }

        return new GuildSyncResult(updated, skipped);
    }
}
