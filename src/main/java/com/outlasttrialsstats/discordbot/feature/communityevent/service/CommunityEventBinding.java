package com.outlasttrialsstats.discordbot.feature.communityevent.service;

import com.outlasttrialsstats.discordbot.entity.CommunityEventMessage;

public record CommunityEventBinding(String guildId, String eventId, String shownEventId, String channelId,
                                    String messageId) {

    public static CommunityEventBinding of(CommunityEventMessage message) {
        return new CommunityEventBinding(message.getGuildId(), message.getEventId(), message.getShownEventId(),
                message.getChannelId(), message.getMessageId());
    }

    public boolean followsCurrentEvent() {
        return eventId == null;
    }
}
