package com.outlasttrialsstats.discordbot.feature.widget.dto;

import java.util.List;
import java.util.Map;

public record WidgetProfilePayload(String username, List<WidgetDynamicField> fields) {

    public Map<String, Object> toRequestBody() {
        return Map.of(
                "username", username,
                "data", Map.of("dynamic", fields));
    }
}
