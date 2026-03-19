package com.outlasttrialsstats.discordbot.config;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${totstats.api.base-url}")
    private String baseUrl;

    @Bean
    @SuppressWarnings("removal")
    public WebClient statsWebClient() {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new JsonNullableModule())
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> {
                    configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper));
                    configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper));
                })
                .build();
    }
}
