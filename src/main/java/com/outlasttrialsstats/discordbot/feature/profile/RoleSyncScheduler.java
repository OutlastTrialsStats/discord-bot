package com.outlasttrialsstats.discordbot.feature.profile;

import com.outlasttrialsstats.discordbot.feature.profile.service.GuildSyncService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoleSyncScheduler {

    private final JDA jda;
    private final GuildSyncService guildSyncService;

    @Scheduled(fixedRate = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    public void syncAllGuilds() {
        log.info("Starting scheduled role sync for all guilds");

        for (Guild guild : jda.getGuilds()) {
            try {
                var updated = new AtomicInteger();
                var skipped = new AtomicInteger();

                guild.loadMembers(member -> {
                    var result = guildSyncService.syncMember(guild, member);
                    if (result.verified() && result.hasChanges()) {
                        updated.incrementAndGet();
                    } else {
                        skipped.incrementAndGet();
                    }
                }).onSuccess(_ ->
                        log.info("Scheduled role sync for guild {}: {} updated, {} skipped",
                                guild.getId(), updated.get(), skipped.get())
                ).onError(error ->
                        log.warn("Failed to load members for guild {}: {} updated, {} skipped before error: {}",
                                guild.getId(), updated.get(), skipped.get(), error.getMessage())
                );
            } catch (Exception e) {
                log.warn("Failed to sync guild {}: {}", guild.getId(), e.getMessage());
            }
        }
    }
}
