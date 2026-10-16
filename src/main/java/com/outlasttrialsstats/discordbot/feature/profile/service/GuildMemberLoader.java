package com.outlasttrialsstats.discordbot.feature.profile.service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GuildMemberLoader {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    public synchronized List<Member> load(Guild guild) {
        try {
            return request(guild);
        } catch (CompletionException e) {
            if (!(e.getCause() instanceof TimeoutException)) {
                throw e;
            }
            log.info("Timed out loading members for guild {}, retrying", guild.getId());
            return request(guild);
        }
    }

    private List<Member> request(Guild guild) {
        return guild.loadMembers().setTimeout(TIMEOUT).get();
    }
}
