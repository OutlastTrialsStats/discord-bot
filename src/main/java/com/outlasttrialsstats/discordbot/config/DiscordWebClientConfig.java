package com.outlasttrialsstats.discordbot.config;

import com.outlasttrialsstats.discordbot.feature.widget.config.WidgetProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(WidgetProperties.class)
public class DiscordWebClientConfig {

    public static final String DISCORD_API_BASE_URL = "https://discord.com/api/v9";

    @Bean
    public WebClient discordWebClient(@Value("${discord.bot.token}") String botToken) {
        return WebClient.builder()
                .baseUrl(DISCORD_API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bot " + botToken)
                .build();
    }
}
