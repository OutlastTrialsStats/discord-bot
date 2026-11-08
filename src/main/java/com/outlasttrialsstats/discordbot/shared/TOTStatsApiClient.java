package com.outlasttrialsstats.discordbot.shared;

import com.outlasttrialsstats.backend.api.model.DiscordBulkProfileRequest;
import com.outlasttrialsstats.backend.api.model.DiscordBulkProfileResponse;
import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import com.outlasttrialsstats.backend.api.model.DiscordRecentVerificationsResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Blocking facade over the TOTStats backend.
 * <p>
 * Non-2xx responses are never decoded as a body: they surface as errors and are mapped to
 * {@link Optional#empty()} (or {@link ProfileLookup.Failed} for profile lookups).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TOTStatsApiClient {

    private final WebClient statsWebClient;

    public Optional<DiscordLeaderboardResponse> getLeaderboard(StatisticType category, int page) {
        return blockOrEmpty(statsWebClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/discord/leaderboard")
                        .queryParam("category", category.getValue())
                        .queryParam("page", page)
                        .build())
                .retrieve()
                .bodyToMono(DiscordLeaderboardResponse.class), "leaderboard for category " + category);
    }

    /**
     * Looks up the profile linked to a Discord user.
     *
     * @return {@link ProfileLookup.Found} on success, {@link ProfileLookup.NotLinked} only on HTTP 404,
     * {@link ProfileLookup.Failed} for any other error (5xx, 429, other 4xx, timeouts, decode errors,
     * empty body)
     */
    public ProfileLookup getProfile(String discordUserId) {
        try {
            return statsWebClient
                    .get()
                    .uri("/api/discord/profile/{discordUserId}", discordUserId)
                    .retrieve()
                    .bodyToMono(DiscordProfileResponse.class)
                    .<ProfileLookup>map(ProfileLookup.Found::new)
                    .onErrorResume(WebClientResponseException.NotFound.class,
                            _ -> Mono.just(new ProfileLookup.NotLinked()))
                    .blockOptional()
                    .orElseGet(() -> {
                        log.warn("Empty profile response for Discord user {}", discordUserId);
                        return new ProfileLookup.Failed("empty response body");
                    });
        } catch (Exception e) {
            log.warn("Failed to fetch profile for Discord user {}: {}", discordUserId, e.getMessage());
            return new ProfileLookup.Failed(String.valueOf(e.getMessage()));
        }
    }

    /**
     * @return the bulk response, or {@link Optional#empty()} if the request failed for any reason
     */
    public Optional<DiscordBulkProfileResponse> getBulkProfiles(List<String> discordUserIds) {
        var request = new DiscordBulkProfileRequest().discordUserIds(discordUserIds);
        return blockOrEmpty(statsWebClient
                .post()
                .uri("/api/discord/profiles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DiscordBulkProfileResponse.class), "bulk profiles");
    }

    public Optional<DiscordRecentVerificationsResponse> getRecentVerifications(int seconds) {
        return blockOrEmpty(statsWebClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/discord/verifications/recent")
                        .queryParam("seconds", seconds)
                        .build())
                .retrieve()
                .bodyToMono(DiscordRecentVerificationsResponse.class), "recent verifications");
    }

    public Optional<List<DiscordCommunityEvent>> getCommunityEvents() {
        return blockOrEmpty(statsWebClient
                .get()
                .uri("/api/discord/community-events")
                .retrieve()
                .bodyToFlux(DiscordCommunityEvent.class)
                .collectList(), "community events");
    }

    private <T> Optional<T> blockOrEmpty(Mono<T> request, String what) {
        try {
            return request.blockOptional();
        } catch (Exception e) {
            log.warn("Failed to fetch {}: {}", what, e.getMessage());
            return Optional.empty();
        }
    }
}
