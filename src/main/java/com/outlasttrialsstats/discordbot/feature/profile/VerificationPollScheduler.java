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
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class VerificationPollScheduler {

    /**
     * Look-back window passed to the backend (allowed range 5-300). Much larger than the poll delay so that
     * a slow poll or a transient failure does not lose verifications; duplicates are filtered via
     * {@link #processedUserIds}.
     */
    static final int POLL_WINDOW_SECONDS = 60;
    private static final int MAX_PROCESSED_SIZE = 1000;

    private final JDA jda;
    private final TOTStatsApiClient statsApiClient;
    private final GuildSyncService guildSyncService;

    private final Set<String> processedUserIds = Collections.synchronizedSet(Collections.newSetFromMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_PROCESSED_SIZE;
                }
            }));

    public VerificationPollScheduler(JDA jda, TOTStatsApiClient statsApiClient, GuildSyncService guildSyncService) {
        this.jda = jda;
        this.statsApiClient = statsApiClient;
        this.guildSyncService = guildSyncService;
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void pollRecentVerifications() {
        var responseOpt = statsApiClient.getRecentVerifications(POLL_WINDOW_SECONDS);
        if (responseOpt.isEmpty() || responseOpt.get().getVerifications() == null
                || responseOpt.get().getVerifications().isEmpty()) {
            return;
        }

        for (DiscordVerificationEntry verification : responseOpt.get().getVerifications()) {
            String discordUserId = verification.getDiscordUserId();
            if (discordUserId == null || processedUserIds.contains(discordUserId)) {
                continue;
            }

            long userId;
            try {
                userId = Long.parseUnsignedLong(discordUserId);
            } catch (NumberFormatException e) {
                log.warn("Ignoring verification with invalid Discord user id '{}'", discordUserId);
                processedUserIds.add(discordUserId);
                continue;
            }

            log.info("Processing new verification for {} ({})",
                    verification.getDisplayName(), discordUserId);

            if (processVerification(verification, discordUserId, userId)) {
                processedUserIds.add(discordUserId);
            }
        }
    }

    /**
     * Syncs the user in every guild the bot shares with them.
     *
     * @return true if the user was handled in every guild (synced or definitively not a member),
     *         false if at least one guild failed and the user should be retried on the next poll
     */
    private boolean processVerification(DiscordVerificationEntry verification, String discordUserId, long userId) {
        boolean complete = true;

        for (Guild guild : jda.getGuilds()) {
            Member member;
            try {
                // The member cache only holds a few members, so ask Discord directly (scheduler thread may block)
                member = guild.retrieveMemberById(userId).complete();
            } catch (ErrorResponseException e) {
                if (e.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER
                        || e.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                    continue;
                }
                complete = false;
                log.warn("Failed to retrieve member {} in guild {}, retrying next poll: {}",
                        discordUserId, guild.getId(), e.getMessage());
                continue;
            } catch (Exception e) {
                complete = false;
                log.warn("Failed to retrieve member {} in guild {}, retrying next poll: {}",
                        discordUserId, guild.getId(), e.getMessage());
                continue;
            }

            if (member == null) {
                continue;
            }

            try {
                var result = guildSyncService.syncMember(guild, member);
                if (result.isLookupFailed()) {
                    // Retry on the next poll while the verification is still in the window
                    complete = false;
                    log.warn("Profile lookup failed for recently verified user {} in guild {}",
                            discordUserId, guild.getId());
                } else if (result.verified() && result.hasChanges()) {
                    log.info("Synced recently verified user {} ({}) in guild {}",
                            verification.getDisplayName(), discordUserId, guild.getId());
                }
            } catch (Exception e) {
                complete = false;
                log.warn("Failed to sync verified user {} in guild {}, retrying next poll: {}",
                        discordUserId, guild.getId(), e.getMessage());
            }
        }

        return complete;
    }
}
