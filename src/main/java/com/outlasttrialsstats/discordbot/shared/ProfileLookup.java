package com.outlasttrialsstats.discordbot.shared;

import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;

/**
 * Outcome of a single profile lookup against the TOTStats backend.
 * <p>
 * Only {@link NotLinked} (HTTP 404) means the Discord account is confirmed to have no linked
 * profile. {@link Failed} covers everything else (5xx, 429, other 4xx, timeouts, decode errors)
 * and must never be treated as "not verified" — callers must not touch roles in that case.
 */
public sealed interface ProfileLookup {

    record Found(DiscordProfileResponse profile) implements ProfileLookup {
    }

    record NotLinked() implements ProfileLookup {
    }

    record Failed(String reason) implements ProfileLookup {
    }
}
