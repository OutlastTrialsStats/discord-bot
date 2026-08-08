package com.outlasttrialsstats.discordbot.shared;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class EnumFormatter {

    private EnumFormatter() {
    }

    /**
     * Formats an UPPER_SNAKE_CASE enum value as a human-readable title, e.g. {@code EPIC_GAMES -> Epic Games}.
     */
    public static String titleCase(String enumValue) {
        return Arrays.stream(enumValue.toLowerCase().split("_"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
