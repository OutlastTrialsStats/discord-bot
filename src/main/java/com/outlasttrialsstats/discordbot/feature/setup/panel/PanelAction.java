package com.outlasttrialsstats.discordbot.feature.setup.panel;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public record PanelAction(String section, String action, List<String> args) {

    public static final String PREFIX = "setup:panel:";
    public static final String VIEW = "view";

    public static String id(String section, String action, Object... args) {
        StringBuilder id = new StringBuilder(PREFIX).append(section).append(':').append(action);
        for (Object arg : args) {
            id.append(':').append(arg);
        }
        return id.toString();
    }

    public static String nav(String section) {
        return id(section, VIEW);
    }

    public static boolean matches(String componentId) {
        return componentId != null && componentId.startsWith(PREFIX);
    }

    public static Optional<PanelAction> parse(String componentId) {
        if (!matches(componentId)) {
            return Optional.empty();
        }
        String[] parts = componentId.substring(PREFIX.length()).split(":");
        if (parts.length < 2) {
            return Optional.empty();
        }
        return Optional.of(new PanelAction(parts[0], parts[1], List.of(Arrays.copyOfRange(parts, 2, parts.length))));
    }

    public String arg(int index) {
        return index < args.size() ? args.get(index) : null;
    }
}
