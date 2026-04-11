package com.outlasttrialsstats.discordbot.feature.profile.dto;

public record GuildSyncResult(int updated, int unchanged, int unverified, int failed) {
}
