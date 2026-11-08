package com.outlasttrialsstats.discordbot.feature.profile;

import com.outlasttrialsstats.discordbot.feature.profile.service.GuildMemberLoader;
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
    private final GuildMemberLoader memberLoader;
    private final GuildSyncService guildSyncService;

    @Scheduled(fixedRate = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    public void syncAllGuilds() {
        log.info("Starting scheduled role sync for all guilds");

        for (Guild guild : jda.getGuilds()) {
            syncGuild(guild);
        }
    }

    private void syncGuild(Guild guild) {
        if (!guildSyncService.tryStartGuildSync(guild.getId())) {
            log.info("Skipping scheduled role sync for guild {}: a sync is already in progress", guild.getId());
            return;
        }
        try {
            guildSyncService.syncMembers(guild, memberLoader.load(guild));
        } catch (Exception e) {
            log.warn("Failed to sync guild {}", guild.getId(), e);
        } finally {
            guildSyncService.finishGuildSync(guild.getId());
        }
    }
}
