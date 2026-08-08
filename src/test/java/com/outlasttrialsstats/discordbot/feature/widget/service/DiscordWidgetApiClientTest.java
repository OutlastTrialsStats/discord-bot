package com.outlasttrialsstats.discordbot.feature.widget.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.outlasttrialsstats.discordbot.feature.widget.config.WidgetProperties;
import com.outlasttrialsstats.discordbot.feature.widget.dto.WidgetDynamicField;
import com.outlasttrialsstats.discordbot.feature.widget.dto.WidgetProfilePayload;
import com.outlasttrialsstats.discordbot.feature.widget.dto.WidgetPushResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class DiscordWidgetApiClientTest {

    private static final String APP_ID = "app-1";
    private static final String USER_ID = "user-1";

    private final AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
    private ClientResponse stubbedResponse = ClientResponse.create(HttpStatus.OK).build();

    private DiscordWidgetApiClient client() {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://discord.com/api/v9")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bot test-token")
                .exchangeFunction(request -> {
                    capturedRequest.set(request);
                    return Mono.just(stubbedResponse);
                })
                .build();
        var properties = new WidgetProperties(true, "https://bot.example.com", 1500);
        return new DiscordWidgetApiClient(webClient, properties, APP_ID, "secret");
    }

    @Test
    void pushProfile_success_sendsPatchWithBotAuth() {
        var payload = new WidgetProfilePayload("Player",
                List.of(WidgetDynamicField.ofNumber("level", 10)));

        var result = client().pushProfile(USER_ID, payload);

        assertThat(result.isSuccess()).isTrue();
        var request = capturedRequest.get();
        assertThat(request.method()).isEqualTo(HttpMethod.PATCH);
        assertThat(request.url().toString())
                .isEqualTo("https://discord.com/api/v9/applications/app-1/users/user-1/identities/0/profile");
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bot test-token");
    }

    @Test
    void pushProfile_forbidden_isRevoked() {
        stubbedResponse = ClientResponse.create(HttpStatus.FORBIDDEN)
                .header("Content-Type", "application/json")
                .body("{\"message\": \"Missing Access\"}")
                .build();

        var result = client().pushProfile(USER_ID, emptyPayload());

        assertThat(result).isInstanceOf(WidgetPushResult.Revoked.class);
        assertThat(((WidgetPushResult.Revoked) result).error()).contains("Missing Access");
    }

    @Test
    void pushProfile_rateLimited_returnsRetryAfter() {
        stubbedResponse = ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "6.5")
                .build();

        var result = client().pushProfile(USER_ID, emptyPayload());

        assertThat(result).isEqualTo(new WidgetPushResult.RateLimited(7));
    }

    @Test
    void pushProfile_serverError_isFailed() {
        stubbedResponse = ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build();

        var result = client().pushProfile(USER_ID, emptyPayload());

        assertThat(result).isInstanceOf(WidgetPushResult.Failed.class);
    }

    @Test
    void clearProfile_sendsPatch() {
        var result = client().clearProfile(USER_ID);

        assertThat(result.isSuccess()).isTrue();
        assertThat(capturedRequest.get().method()).isEqualTo(HttpMethod.PATCH);
    }

    @Test
    void exchangeCode_returnsAccessToken_withoutBotAuth() {
        stubbedResponse = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("{\"access_token\": \"token-123\", \"token_type\": \"Bearer\"}")
                .build();

        var token = client().exchangeCode("code-abc");

        assertThat(token).contains("token-123");
        var request = capturedRequest.get();
        assertThat(request.method()).isEqualTo(HttpMethod.POST);
        assertThat(request.url().toString()).isEqualTo("https://discord.com/api/v9/oauth2/token");
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void exchangeCode_badRequest_returnsEmpty() {
        stubbedResponse = ClientResponse.create(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("{\"error\": \"invalid_grant\"}")
                .build();

        assertThat(client().exchangeCode("bad-code")).isEmpty();
    }

    @Test
    void fetchAuthorizedUserId_parsesUserId_withBearerAuth() {
        stubbedResponse = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("{\"user\": {\"id\": \"user-1\", \"username\": \"player\"}}")
                .build();

        var userId = client().fetchAuthorizedUserId("token-123");

        assertThat(userId).contains("user-1");
        assertThat(capturedRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer token-123");
    }

    @Test
    void fetchAuthorizedUserId_unauthorized_returnsEmpty() {
        stubbedResponse = ClientResponse.create(HttpStatus.UNAUTHORIZED).build();

        assertThat(client().fetchAuthorizedUserId("expired")).isEmpty();
    }

    private static WidgetProfilePayload emptyPayload() {
        return new WidgetProfilePayload("Player", List.of());
    }
}
