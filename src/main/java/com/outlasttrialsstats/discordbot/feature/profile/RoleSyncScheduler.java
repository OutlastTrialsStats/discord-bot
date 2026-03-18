package com.outlasttrialsstats.discordbot.feature.profile;

import com.outlasttrialsstats.discordbot.feature.profile.service.GuildSyncService;
import java.util.concurrent.TimeUnit;
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
                guild.loadMembers().onSuccess(members -> {
                    var result = guildSyncService.syncMembers(guild, members);
                    log.info("Scheduled role sync for guild {}: {} updated, {} skipped",
                            guild.getId(), result.updated(), result.skipped());
                }).onError(error ->
                        log.warn("Failed to load members for guild {}: {}", guild.getId(), error.getMessage())
                );
            } catch (Exception e) {
                log.warn("Failed to sync guild {}: {}", guild.getId(), e.getMessage());
            }
        }
    }
}
