package com.outlasttrialsstats.discordbot.entity;

public enum CommunityAlertType {

    MILESTONE_REACHED(true, null),
    PRE_ALERT(true, 60);

    private final boolean enabledByDefault;
    private final Integer defaultLeadTimeMinutes;

    CommunityAlertType(boolean enabledByDefault, Integer defaultLeadTimeMinutes) {
        this.enabledByDefault = enabledByDefault;
        this.defaultLeadTimeMinutes = defaultLeadTimeMinutes;
    }

    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    public Integer getDefaultLeadTimeMinutes() {
        return defaultLeadTimeMinutes;
    }

    public boolean hasLeadTime() {
        return defaultLeadTimeMinutes != null;
    }
}
