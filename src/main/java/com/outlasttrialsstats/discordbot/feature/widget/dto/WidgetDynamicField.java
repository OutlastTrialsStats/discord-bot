package com.outlasttrialsstats.discordbot.feature.widget.dto;

import java.util.Map;

/**
 * A single dynamic widget field. Discord types: 1 = string, 2 = number, 3 = image.
 */
public record WidgetDynamicField(int type, String name, Object value) {

    public static WidgetDynamicField ofString(String name, String value) {
        return new WidgetDynamicField(1, name, value);
    }

    public static WidgetDynamicField ofNumber(String name, Number value) {
        return new WidgetDynamicField(2, name, value);
    }

    public static WidgetDynamicField ofImage(String name, String url) {
        return new WidgetDynamicField(3, name, Map.of("url", url));
    }
}
