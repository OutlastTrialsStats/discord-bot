package com.outlasttrialsstats.discordbot.shared;

public final class Texts {

    private Texts() {
    }

    public static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
