package com.outlasttrialsstats.discordbot.shared;

import java.util.Collection;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

@Slf4j
public final class MessageCleanup {

    private MessageCleanup() {
    }

    public static void deleteMessages(MessageChannel channel, Collection<String> messageIds, Set<String> skip) {
        for (String messageId : messageIds) {
            if (skip.contains(messageId)) {
                continue;
            }
            try {
                channel.deleteMessageById(messageId).queue(
                        _ -> {},
                        error -> log.debug("Could not delete message {}: {}", messageId, error.getMessage()));
            } catch (Exception e) {
                log.debug("Could not delete message {}: {}", messageId, e.getMessage());
            }
        }
    }
}
