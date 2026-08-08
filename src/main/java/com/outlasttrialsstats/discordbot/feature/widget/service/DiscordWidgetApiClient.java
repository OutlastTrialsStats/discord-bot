package com.outlasttrialsstats.discordbot.feature.widget.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.outlasttrialsstats.discordbot.feature.widget.config.WidgetProperties;
import com.outlasttrialsstats.discordbot.feature.widget.dto.WidgetProfilePayload;
import com.outlasttrialsstats.discordbot.feature.widget.dto.WidgetPushResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Client for Discord's (experimental, undocumented) Social SDK profile widget API
 * and the OAuth2 endpoints used to complete widget authorization.
 */
@Service
@Slf4j
public class DiscordWidgetApiClient {

    private final WebClient discordWebClient;
    private final WebClient oauthWebClient;
    private final WidgetProperties widgetProperties;
    private final String applicationId;
    private final String clientSecret;

    public DiscordWidgetApiClient(WebClient discordWebClient,
                                  WidgetProperties widgetProperties,
                                  @Value("${discord.application-id}") String applicationId,
                                  @Value("${discord.oauth.client-secret}") String clientSecret) {
        this.discordWebClient = discordWebClient;
        // The token exchange must not carry the bot Authorization default header;
        // request-level header removal cannot undo WebClient defaults.
        this.oauthWebClient = discordWebClient.mutate()
                .defaultHeaders(headers -> headers.remove(HttpHeaders.AUTHORIZATION))
                .build();
        this.widgetProperties = widgetProperties;
        this.applicationId = applicationId;
        this.clientSecret = clientSecret;
    }

    public WidgetPushResult pushProfile(String discordUserId, WidgetProfilePayload payload) {
        return patchIdentityProfile(discordUserId, payload.toRequestBody());
    }

    public WidgetPushResult clearProfile(String discordUserId) {
        return patchIdentityProfile(discordUserId, Map.of("data", Map.of("dynamic", List.of())));
    }

    private WidgetPushResult patchIdentityProfile(String discordUserId, Map<String, Object> body) {
        try {
            discordWebClient
                    .patch()
                    .uri("/applications/{applicationId}/users/{userId}/identities/0/profile",
                            applicationId, discordUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return new WidgetPushResult.Success();
        } catch (WebClientResponseException e) {
            return mapError(discordUserId, e);
        } catch (Exception e) {
            log.warn("Widget profile update failed for user {}: {}", discordUserId, e.getMessage());
            return new WidgetPushResult.Failed(e.getMessage());
        }
    }

    private WidgetPushResult mapError(String discordUserId, WebClientResponseException e) {
        String responseBody = e.getResponseBodyAsString();
        if (e.getStatusCode() == HttpStatus.UNAUTHORIZED
                || e.getStatusCode() == HttpStatus.FORBIDDEN
                || e.getStatusCode() == HttpStatus.NOT_FOUND) {
            log.info("Widget authorization missing or revoked for user {} ({}): {}",
                    discordUserId, e.getStatusCode(), responseBody);
            return new WidgetPushResult.Revoked(e.getStatusCode() + ": " + responseBody);
        }
        if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            long retryAfter = parseRetryAfter(e.getHeaders());
            log.warn("Widget update rate limited for user {}, retry after {}s", discordUserId, retryAfter);
            return new WidgetPushResult.RateLimited(retryAfter);
        }
        log.warn("Widget profile update failed for user {} ({}): {}",
                discordUserId, e.getStatusCode(), responseBody);
        return new WidgetPushResult.Failed(e.getStatusCode() + ": " + responseBody);
    }

    private static long parseRetryAfter(HttpHeaders headers) {
        try {
            String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
            return retryAfter != null ? (long) Math.ceil(Double.parseDouble(retryAfter)) : 5;
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    /**
     * Exchanges an OAuth2 authorization code for an access token. The token is only
     * needed to verify who authorized; it is not stored.
     */
    public Optional<String> exchangeCode(String code) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", applicationId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", widgetProperties.callbackUrl());

        try {
            TokenResponse response = oauthWebClient
                    .post()
                    .uri("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();
            return Optional.ofNullable(response).map(TokenResponse::accessToken);
        } catch (WebClientResponseException e) {
            log.warn("OAuth token exchange failed ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("OAuth token exchange failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns the Discord user id that the given access token was issued for.
     */
    public Optional<String> fetchAuthorizedUserId(String accessToken) {
        try {
            AuthorizationInfo response = discordWebClient
                    .get()
                    .uri("/oauth2/@me")
                    .headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .retrieve()
                    .bodyToMono(AuthorizationInfo.class)
                    .block();
            return Optional.ofNullable(response)
                    .map(AuthorizationInfo::user)
                    .map(AuthorizationInfo.AuthorizedUser::id);
        } catch (Exception e) {
            log.warn("Failed to fetch authorized user: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AuthorizationInfo(AuthorizedUser user) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record AuthorizedUser(String id) {}
    }
}
