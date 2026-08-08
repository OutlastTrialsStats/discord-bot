package com.outlasttrialsstats.discordbot.feature.widget;

/**
 * Dynamic field names pushed to the Discord profile widget.
 * These MUST match the field names defined in the widget layout editor
 * in the Discord Developer Portal.
 */
public final class WidgetFields {

    public static final String PRESTIGE = "prestige";
    public static final String LEVEL = "level";
    public static final String COMPLETED_TRIALS = "completed_trials";
    public static final String TRIALS_IN_HOURS = "trials_in_hours";
    public static final String DEATHS = "deaths";
    public static final String REAGENT_RELEASES = "reagent_releases";
    public static final String INVASION_RANK = "invasion_rank";
    public static final String PLATFORM = "platform";

    private WidgetFields() {
    }
}
