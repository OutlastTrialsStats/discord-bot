package com.outlasttrialsstats.discordbot.feature.communityevent.listener;

import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventBinding;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventService;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommunityEventMessageDeleteListener extends ListenerAdapter {

    private final CommunityEventService service;

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        handleDeletedMessages(List.of(event.getMessageId()));
    }

    @Override
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        handleDeletedMessages(event.getMessageIds());
    }

    private void handleDeletedMessages(Collection<String> messageIds) {
        for (CommunityEventBinding binding : service.removeBindingsByMessageIds(messageIds)) {
            log.info("Community event message deleted in guild {}, removing it", binding.guildId());
        }
    }
}
