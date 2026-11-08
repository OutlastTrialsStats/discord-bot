package com.outlasttrialsstats.discordbot.shared;

/**
 * Published when the custom messages of a guild were changed. {@link MessageService} drops its cached
 * messages for the guild once the surrounding transaction has committed.
 */
public record GuildMessagesChangedEvent(String guildId) {
}
