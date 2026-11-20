package com.outlasttrialsstats.discordbot.shared;

import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;

public sealed interface ProfileLookup {

    record Found(DiscordProfileResponse profile) implements ProfileLookup {
    }

    record NotLinked() implements ProfileLookup {
    }

    record Failed(String reason) implements ProfileLookup {
    }
}
