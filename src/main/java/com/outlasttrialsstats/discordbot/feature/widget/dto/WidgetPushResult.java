package com.outlasttrialsstats.discordbot.feature.widget.dto;

public sealed interface WidgetPushResult {

    record Success() implements WidgetPushResult {}

    record Revoked(String error) implements WidgetPushResult {}

    record RateLimited(long retryAfterSeconds) implements WidgetPushResult {}

    record Failed(String error) implements WidgetPushResult {}

    default boolean isSuccess() {
        return this instanceof Success;
    }
}
