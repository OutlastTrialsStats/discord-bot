package com.outlasttrialsstats.discordbot.feature.profile;

import com.outlasttrialsstats.backend.api.model.DiscordVerificationEntry;
import com.outlasttrialsstats.discordbot.feature.profile.service.GuildSyncService;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class VerificationPollScheduler {

    private static final int POLL_WINDOW_SECONDS = 10;
    private static final int MAX_PROCESSED_SIZE = 1000;

    private final JDA jda;
    private final TOTStatsApiClient statsApiClient;
    private final GuildSyncService guildSyncService;

    private final Set<String> processedUserIds = Collections.newSetFromMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_PROCESSED_SIZE;
                }
            });

    public VerificationPollScheduler(JDA jda, TOTStatsApiClient statsApiClient, GuildSyncService guildSyncService) {
        this.jda = jda;
        this.statsApiClient = statsApiClient;
        this.guildSyncService = guildSyncService;
    }

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
    public void pollRecentVerifications() {
        var responseOpt = statsApiClient.getRecentVerifications(POLL_WINDOW_SECONDS);
        if (responseOpt.isEmpty() || responseOpt.get().getVerifications() == null
                || responseOpt.get().getVerifications().isEmpty()) {
            return;
        }

        for (DiscordVerificationEntry verification : responseOpt.get().getVerifications()) {
            String discordUserId = verification.getDiscordUserId();

            if (!processedUserIds.add(discordUserId)) {
                continue;
            }

            log.info("Processing new verification for {} ({})",
                    verification.getDisplayName(), discordUserId);

            for (Guild guild : jda.getGuilds()) {
                Member member = guild.getMemberById(discordUserId);
                if (member == null) {
                    continue;
                }

                var result = guildSyncService.syncMember(guild, member);
                if (result.verified() && result.hasChanges()) {
                    log.info("Synced recently verified user {} ({}) in guild {}",
                            verification.getDisplayName(), discordUserId, guild.getId());
                }
            }
        }
    }
}
