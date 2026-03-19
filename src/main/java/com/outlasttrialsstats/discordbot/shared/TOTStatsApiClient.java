package com.outlasttrialsstats.discordbot.shared;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class TOTStatsApiClient {

    private final WebClient statsWebClient;

    public Optional<DiscordLeaderboardResponse> getLeaderboard(StatisticType category, int page) {
        try {
            return statsWebClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/discord/leaderboard")
                            .queryParam("category", category.getValue())
                            .queryParam("page", page)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, _ -> Mono.empty())
                    .bodyToMono(DiscordLeaderboardResponse.class)
                    .blockOptional();
        } catch (Exception e) {
            log.warn("Failed to fetch leaderboard for category {}: {}", category, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<DiscordProfileResponse> getProfile(String discordUserId) {
        try {
            return statsWebClient
                    .get()
                    .uri("/api/discord/profile/{discordUserId}", discordUserId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, _ -> Mono.empty())
                    .bodyToMono(DiscordProfileResponse.class)
                    .blockOptional();
        } catch (Exception e) {
            log.warn("Failed to fetch profile for Discord user {}: {}", discordUserId, e.getMessage());
            return Optional.empty();
        }
    }
}
