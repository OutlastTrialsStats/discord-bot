package com.outlasttrialsstats.discordbot.feature.guild;

import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemberCountScheduler {

    private final JDA jda;
    private final GuildServerRepository guildServerRepository;

    @Scheduled(fixedRate = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    public void updateMemberCounts() {
        for (Guild guild : jda.getGuilds()) {
            try {
                int count = guild.getMemberCount();
                GuildServer server = guildServerRepository.findById(guild.getId())
                        .orElseGet(() -> new GuildServer(guild.getId()));
                server.setMemberCount(count);
                guildServerRepository.save(server);
                log.debug("Updated member count for guild {}: {}", guild.getId(), count);
            } catch (Exception e) {
                log.warn("Failed to update member count for guild {}: {}", guild.getId(), e.getMessage());
            }
        }
    }
}
