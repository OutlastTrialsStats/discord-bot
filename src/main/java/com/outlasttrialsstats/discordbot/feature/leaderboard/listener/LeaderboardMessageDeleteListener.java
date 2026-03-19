package com.outlasttrialsstats.discordbot.feature.leaderboard.listener;

import com.outlasttrialsstats.discordbot.repository.LeaderboardChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class LeaderboardMessageDeleteListener extends ListenerAdapter {

    private final LeaderboardChannelRepository leaderboardChannelRepository;

    @Override
    @Transactional
    public void onMessageDelete(MessageDeleteEvent event) {
        String messageId = event.getMessageId();

        leaderboardChannelRepository.findByMessageId(messageId).ifPresent(binding -> {
            log.info("Leaderboard message {} deleted in guild {}, removing binding for category {}",
                    messageId, binding.getGuildId(), binding.getCategory());
            leaderboardChannelRepository.delete(binding);
        });
    }
}
