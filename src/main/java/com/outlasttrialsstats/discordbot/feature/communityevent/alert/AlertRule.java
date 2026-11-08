package com.outlasttrialsstats.discordbot.feature.communityevent.alert;

import com.outlasttrialsstats.discordbot.entity.CommunityAlertType;
import com.outlasttrialsstats.discordbot.entity.CommunityEventAlertRule;

public record AlertRule(CommunityAlertType type, boolean enabled, String roleId, Integer leadTimeMinutes) {

    public static AlertRule defaults(CommunityAlertType type) {
        return new AlertRule(type, type.isEnabledByDefault(), null, type.getDefaultLeadTimeMinutes());
    }

    public static AlertRule of(CommunityEventAlertRule rule) {
        Integer leadTime = rule.getLeadTimeMinutes() != null
                ? rule.getLeadTimeMinutes() : rule.getAlertType().getDefaultLeadTimeMinutes();
        return new AlertRule(rule.getAlertType(), rule.isEnabled(), rule.getRoleId(), leadTime);
    }
}
