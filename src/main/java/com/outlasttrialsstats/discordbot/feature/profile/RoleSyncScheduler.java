package com.outlasttrialsstats.discordbot.feature.profile;

import com.outlasttrialsstats.backend.api.model.DiscordBulkProfileEntry;
import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import com.outlasttrialsstats.discordbot.feature.profile.service.RoleAssignmentService;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoleSyncScheduler {

    private static final int BULK_BATCH_SIZE = 100;

    private final JDA jda;
    private final TOTStatsApiClient statsApiClient;
    private final RoleAssignmentService roleAssignmentService;

    @Scheduled(fixedRate = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    public void syncAllGuilds() {
        log.info("Starting scheduled role sync for all guilds");

        for (Guild guild : jda.getGuilds()) {
            try {
                guild.loadMembers()
                        .onSuccess(members -> syncGuildMembers(guild, members))
                        .onError(error -> log.warn("Failed to load members for guild {}: {}",
                                guild.getId(), error.getMessage()));
            } catch (Exception e) {
                log.warn("Failed to sync guild {}: {}", guild.getId(), e.getMessage());
            }
        }
    }

    void syncGuildMembers(Guild guild, List<Member> members) {
        List<Member> nonBotMembers = members.stream()
                .filter(m -> !m.getUser().isBot())
                .toList();

        var updated = new AtomicInteger();
        var skipped = new AtomicInteger();

        for (int i = 0; i < nonBotMembers.size(); i += BULK_BATCH_SIZE) {
            List<Member> batch = nonBotMembers.subList(i, Math.min(i + BULK_BATCH_SIZE, nonBotMembers.size()));
            List<String> discordIds = batch.stream().map(Member::getId).toList();

            var responseOpt = statsApiClient.getBulkProfiles(discordIds);
            if (responseOpt.isEmpty() || responseOpt.get().getProfiles() == null) {
                skipped.addAndGet(batch.size());
                continue;
            }

            Map<String, DiscordBulkProfileEntry> profilesByDiscordId = responseOpt.get().getProfiles().stream()
                    .collect(Collectors.toMap(DiscordBulkProfileEntry::getDiscordUserId, Function.identity()));

            for (Member member : batch) {
                var entry = profilesByDiscordId.get(member.getId());
                if (entry == null) {
                    skipped.incrementAndGet();
                    continue;
                }

                try {
                    var profile = toProfileResponse(entry);
                    var result = roleAssignmentService.assignRolesFromProfile(guild, member, profile);
                    if (result.hasChanges()) {
                        updated.incrementAndGet();
                    } else {
                        skipped.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.warn("Failed to sync roles for member {} in guild {}: {}",
                            member.getId(), guild.getId(), e.getMessage());
                    skipped.incrementAndGet();
                }
            }
        }

        log.info("Scheduled role sync for guild {}: {} updated, {} skipped",
                guild.getId(), updated.get(), skipped.get());
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
