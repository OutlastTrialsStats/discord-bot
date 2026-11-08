package com.outlasttrialsstats.discordbot.feature.profile.service;

import com.outlasttrialsstats.backend.api.model.DiscordBulkProfileEntry;
import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.feature.profile.dto.GuildSyncResult;
import com.outlasttrialsstats.discordbot.feature.profile.dto.RoleAssignmentResult;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuildSyncService {

    static final int BULK_BATCH_SIZE = 100;

    private final RoleAssignmentService roleAssignmentService;
    private final TOTStatsApiClient statsApiClient;
    private final GuildServerRepository guildServerRepository;

    /** Guilds with a full member sync currently running (shared by /sync-all and the scheduler). */
    private final Set<String> syncsInProgress = ConcurrentHashMap.newKeySet();

    public RoleAssignmentResult syncMember(Guild guild, Member member) {
        if (member.getUser().isBot()) {
            return RoleAssignmentResult.notVerified();
        }

        return roleAssignmentService.assignRoles(guild, member);
    }

    /**
     * Marks a full sync of the guild as running. Returns {@code false} if one is already in progress.
     * Every successful call must be paired with {@link #finishGuildSync(String)}.
     */
    public boolean tryStartGuildSync(String guildId) {
        return syncsInProgress.add(guildId);
    }

    public void finishGuildSync(String guildId) {
        syncsInProgress.remove(guildId);
    }

    /**
     * Syncs the roles of all non-bot members using the bulk profile endpoint in batches of
     * {@value #BULK_BATCH_SIZE}. Members missing from a successful batch response are confirmed as
     * not linked and cleaned up; members of a failed batch are counted as failed and left untouched.
     * The verified member count is only stored if every batch succeeded.
     */
    public GuildSyncResult syncMembers(Guild guild, List<Member> members) {
        List<Member> nonBotMembers = members.stream()
                .filter(m -> !m.getUser().isBot())
                .toList();

        int updated = 0;
        int unchanged = 0;
        int unverified = 0;
        int failed = 0;
        int failedBatches = 0;
        // Loaded on the first unlinked member and reused for the whole sync
        List<String> mappedRoleIds = null;

        for (int i = 0; i < nonBotMembers.size(); i += BULK_BATCH_SIZE) {
            List<Member> batch = nonBotMembers.subList(i, Math.min(i + BULK_BATCH_SIZE, nonBotMembers.size()));
            List<String> discordIds = batch.stream().map(Member::getId).toList();

            var responseOpt = statsApiClient.getBulkProfiles(discordIds);
            if (responseOpt.isEmpty() || responseOpt.get().getProfiles() == null) {
                // Backend unavailable: never treat this as "not linked", leave roles untouched
                log.warn("Bulk profile lookup failed for {} members in guild {}, skipping batch",
                        batch.size(), guild.getId());
                failedBatches++;
                failed += batch.size();
                continue;
            }

            Map<String, DiscordBulkProfileEntry> profilesByDiscordId = responseOpt.get().getProfiles().stream()
                    .filter(Objects::nonNull)
                    .filter(entry -> entry.getDiscordUserId() != null)
                    .collect(Collectors.toMap(DiscordBulkProfileEntry::getDiscordUserId, Function.identity(),
                            (first, _) -> first));

            for (Member member : batch) {
                var entry = profilesByDiscordId.get(member.getId());
                try {
                    if (entry == null) {
                        if (mappedRoleIds == null) {
                            mappedRoleIds = roleAssignmentService.getMappedRoleIds(guild.getId());
                        }
                        roleAssignmentService.cleanupUnverifiedMember(guild, member, mappedRoleIds);
                        unverified++;
                        continue;
                    }

                    var result = roleAssignmentService.assignRolesFromProfile(guild, member, toProfileResponse(entry));
                    if (result.hasChanges()) {
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
        }

        if (failedBatches == 0) {
            saveVerifiedMemberCount(guild, updated + unchanged);
        } else {
            log.warn("Not updating verified member count for guild {}: {} batch(es) failed",
                    guild.getId(), failedBatches);
        }

        log.info("Role sync for guild {}: {} updated, {} unchanged, {} unverified, {} failed",
                guild.getId(), updated, unchanged, unverified, failed);
        return new GuildSyncResult(updated, unchanged, unverified, failed);
    }

    private void saveVerifiedMemberCount(Guild guild, int verified) {
        try {
            GuildServer server = guildServerRepository.findById(guild.getId())
                    .orElseGet(() -> new GuildServer(guild.getId()));
            server.setVerifiedMemberCount(verified);
            guildServerRepository.save(server);
        } catch (Exception e) {
            log.warn("Failed to save verified member count for guild {}: {}", guild.getId(), e.getMessage());
        }
    }

    private DiscordProfileResponse toProfileResponse(DiscordBulkProfileEntry entry) {
        var profile = new DiscordProfileResponse();
        profile.setProfileId(entry.getProfileId());
        profile.setDisplayName(entry.getDisplayName());
        profile.setPrestigeLevel(entry.getPrestigeLevel());
        profile.setLevel(entry.getLevel());
        profile.setActiveReagentSkill(entry.getActiveReagentSkill());
        profile.setInvasionRanking(entry.getInvasionRanking());
        profile.setTotalInvasionMatchesPlayed(entry.getTotalInvasionMatchesPlayed());
        profile.setSeasonTotalInvasionPoints(entry.getSeasonTotalInvasionPoints());
        profile.setPlatformType(entry.getPlatformType());
        profile.setAccountCreationType(entry.getAccountCreationType());
        return profile;
    }
}
