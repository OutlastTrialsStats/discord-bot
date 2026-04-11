package com.outlasttrialsstats.discordbot.feature.profile.service;

import com.outlasttrialsstats.discordbot.feature.profile.dto.GuildSyncResult;
import com.outlasttrialsstats.discordbot.feature.profile.dto.RoleAssignmentResult;
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

    public RoleAssignmentResult syncMember(Guild guild, Member member) {
        if (member.getUser().isBot()) {
            return RoleAssignmentResult.notVerified();
        }

        return roleAssignmentService.assignRoles(guild, member);
    }

    public GuildSyncResult syncMembers(Guild guild, List<Member> members) {
        int updated = 0;
        int unchanged = 0;
        int unverified = 0;
        int failed = 0;

        for (var member : members) {
            try {
                var result = syncMember(guild, member);
                if (!result.verified()) {
                    unverified++;
                } else if (result.hasChanges()) {
                    updated++;
                } else {
                    unchanged++;
                }
            } catch (Exception e) {
                log.warn("Failed to sync roles for member {} in guild {}: {}",
                        member.getId(), guild.getId(), e.getMessage());
                failed++;
            }
        }

        return new GuildSyncResult(updated, unchanged, unverified, failed);
    }
}
