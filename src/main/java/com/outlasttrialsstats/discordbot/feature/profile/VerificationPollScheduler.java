package com.outlasttrialsstats.discordbot.feature.profile;

import com.outlasttrialsstats.backend.api.model.DiscordVerificationEntry;
import com.outlasttrialsstats.discordbot.feature.profile.service.GuildSyncService;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.util.concurrent.TimeUnit;
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
public class VerificationPollScheduler {

    private static final int POLL_WINDOW_SECONDS = 10;

    private final JDA jda;
    private final TOTStatsApiClient statsApiClient;
    private final GuildSyncService guildSyncService;

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
    public void pollRecentVerifications() {
        var responseOpt = statsApiClient.getRecentVerifications(POLL_WINDOW_SECONDS);
        if (responseOpt.isEmpty() || responseOpt.get().getVerifications() == null
                || responseOpt.get().getVerifications().isEmpty()) {
            return;
        }

        var verifications = responseOpt.get().getVerifications();
        log.info("Found {} recent verifications, syncing roles", verifications.size());

        for (DiscordVerificationEntry verification : verifications) {
            String discordUserId = verification.getDiscordUserId();

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
