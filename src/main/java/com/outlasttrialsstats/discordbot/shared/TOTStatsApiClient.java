package com.outlasttrialsstats.discordbot.shared;

import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class TOTStatsApiClient {

    private final WebClient statsWebClient;

    public Optional<DiscordProfileResponse> getProfile(String discordUserId) {
        try {
            return statsWebClient
                    .get()
                    .uri("/api/discord/profile/{discordUserId}", discordUserId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, _ -> {
                        throw new RuntimeException("Profile not found for Discord user: " + discordUserId);
                    })
                    .bodyToMono(DiscordProfileResponse.class)
                    .blockOptional();
        } catch (Exception e) {
            log.debug("Profile not found for Discord user {}", discordUserId);
            return Optional.empty();
        }
    }
}
