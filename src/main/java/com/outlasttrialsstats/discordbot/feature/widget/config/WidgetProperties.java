package com.outlasttrialsstats.discordbot.feature.widget.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "widget")
public record WidgetProperties(
        boolean enabled,
        String publicBaseUrl,
        long refreshCooldownMs) {

    public String callbackUrl() {
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + "/oauth/widget/callback";
    }
}
