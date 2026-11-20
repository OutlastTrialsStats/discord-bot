package com.outlasttrialsstats.discordbot.feature.leaderboard.listener;

import com.outlasttrialsstats.discordbot.shared.MessageCleanup;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardBinding;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LeaderboardMessageDeleteListener extends ListenerAdapter {

    private final LeaderboardService leaderboardService;

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        handleDeletedMessages(event.getChannel(), List.of(event.getMessageId()));
    }

    @Override
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        handleDeletedMessages(event.getChannel(), event.getMessageIds());
    }

    private void handleDeletedMessages(MessageChannel channel, Collection<String> deletedMessageIds) {
        List<LeaderboardBinding> removed = leaderboardService.removeBindingsByMessageIds(deletedMessageIds);
        if (removed.isEmpty()) {
            return;
        }
        Set<String> deleted = Set.copyOf(deletedMessageIds);

        for (LeaderboardBinding binding : removed) {
            log.info("Leaderboard message deleted in guild {}, removing binding for category {}",
                    binding.guildId(), binding.category());
            if (binding.channelId().equals(channel.getId())) {
                MessageCleanup.deleteMessages(channel, binding.messageIds(), deleted);
            }
        }
    }
}
