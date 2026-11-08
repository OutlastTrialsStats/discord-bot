package com.outlasttrialsstats.discordbot.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.outlasttrialsstats.backend.api.model.PlatformType;
import com.outlasttrialsstats.discordbot.config.WebClientConfig;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerResponse;

/**
 * Runs the real WebClient (codecs, timeouts) against an in-process reactor-netty server.
 */
class TOTStatsApiClientTest {

    private static final Duration RESPONSE_TIMEOUT = Duration.ofMillis(300);

    private final AtomicReference<Responder> responder = new AtomicReference<>();
    private DisposableServer server;
    private TOTStatsApiClient client;

    @FunctionalInterface
    private interface Responder {
        Mono<Void> respond(HttpServerResponse response);
    }

    @BeforeEach
    void setUp() {
        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((_, response) -> responder.get().respond(response))
                .bindNow();
        var webClient = WebClientConfig.createStatsWebClient(
                "http://127.0.0.1:" + server.port(), Duration.ofSeconds(1), RESPONSE_TIMEOUT);
        client = new TOTStatsApiClient(webClient);
    }

    @AfterEach
    void tearDown() {
        server.disposeNow();
    }

    @Test
    void getProfile_200_returnsFound() {
        respondJson(HttpResponseStatus.OK, """
                {"displayName":"Player","prestigeLevel":12,"platformType":"STEAM"}
                """);

        var result = client.getProfile("user-1");

        assertThat(result).isInstanceOf(ProfileLookup.Found.class);
        var profile = ((ProfileLookup.Found) result).profile();
        assertThat(profile.getDisplayName()).isEqualTo("Player");
        assertThat(profile.getPrestigeLevel()).isEqualTo(12);
        assertThat(profile.getPlatformType()).isEqualTo(PlatformType.STEAM);
    }

    @Test
    void getProfile_unknownFieldAndEnumValue_returnsFoundWithNullEnum() {
        respondJson(HttpResponseStatus.OK, """
                {"displayName":"Player","prestigeLevel":3,"platformType":"NINTENDO_SWITCH","brandNewField":{"x":1}}
                """);

        var result = client.getProfile("user-1");

        assertThat(result).isInstanceOf(ProfileLookup.Found.class);
        var profile = ((ProfileLookup.Found) result).profile();
        assertThat(profile.getPrestigeLevel()).isEqualTo(3);
        assertThat(profile.getPlatformType()).isNull();
    }

    @Test
    void getProfile_404_returnsNotLinked() {
        respondJson(HttpResponseStatus.NOT_FOUND, """
                {"error":"not found"}
                """);

        assertThat(client.getProfile("user-1")).isInstanceOf(ProfileLookup.NotLinked.class);
    }

    @Test
    void getProfile_503_returnsFailed() {
        respondJson(HttpResponseStatus.SERVICE_UNAVAILABLE, "{}");

        assertThat(client.getProfile("user-1")).isInstanceOf(ProfileLookup.Failed.class);
    }

    @Test
    void getProfile_429_returnsFailed() {
        respondJson(HttpResponseStatus.TOO_MANY_REQUESTS, "{}");

        assertThat(client.getProfile("user-1")).isInstanceOf(ProfileLookup.Failed.class);
    }

    @Test
    void getProfile_otherClientError_returnsFailed() {
        respondJson(HttpResponseStatus.BAD_REQUEST, "{}");

        assertThat(client.getProfile("user-1")).isInstanceOf(ProfileLookup.Failed.class);
    }

    @Test
    void getProfile_malformedBody_returnsFailed() {
        respondJson(HttpResponseStatus.OK, "{not json");

        assertThat(client.getProfile("user-1")).isInstanceOf(ProfileLookup.Failed.class);
    }

    @Test
    void getProfile_timeout_returnsFailed() {
        responder.set(response -> Mono.delay(RESPONSE_TIMEOUT.multipliedBy(5))
                .then(response.status(HttpResponseStatus.OK)
                        .header("Content-Type", "application/json")
                        .sendString(Mono.just("{\"displayName\":\"late\"}"))
                        .then()));

        assertThat(client.getProfile("user-1")).isInstanceOf(ProfileLookup.Failed.class);
    }

    @Test
    void getProfile_connectionRefused_returnsFailed() {
        int port = server.port();
        server.disposeNow();
        var unreachable = new TOTStatsApiClient(WebClientConfig.createStatsWebClient(
                "http://127.0.0.1:" + port, Duration.ofSeconds(1), RESPONSE_TIMEOUT));

        assertThat(unreachable.getProfile("user-1")).isInstanceOf(ProfileLookup.Failed.class);

        // restart so tearDown has something to dispose
        server = HttpServer.create().host("127.0.0.1").port(0)
                .handle((_, response) -> response.send()).bindNow();
    }

    @Test
    void getBulkProfiles_serverError_returnsEmpty() {
        respondJson(HttpResponseStatus.INTERNAL_SERVER_ERROR, "{}");

        assertThat(client.getBulkProfiles(List.of("user-1"))).isEmpty();
    }

    @Test
    void getBulkProfiles_clientError_returnsEmptyInsteadOfDecodingErrorBody() {
        respondJson(HttpResponseStatus.BAD_REQUEST, """
                {"profiles":[]}
                """);

        assertThat(client.getBulkProfiles(List.of("user-1"))).isEmpty();
    }

    @Test
    void getBulkProfiles_unknownField_isTolerated() {
        respondJson(HttpResponseStatus.OK, """
                {"profiles":[{"discordUserId":"user-1","prestigeLevel":5,"somethingNew":true}],"extra":1}
                """);

        var result = client.getBulkProfiles(List.of("user-1"));

        assertThat(result).isPresent();
        assertThat(result.get().getProfiles()).hasSize(1);
        assertThat(result.get().getProfiles().getFirst().getPrestigeLevel()).isEqualTo(5);
    }

    private void respondJson(HttpResponseStatus status, String body) {
        responder.set(response -> response.status(status)
                .header("Content-Type", "application/json")
                .sendString(Mono.just(body))
                .then());
    }
}
