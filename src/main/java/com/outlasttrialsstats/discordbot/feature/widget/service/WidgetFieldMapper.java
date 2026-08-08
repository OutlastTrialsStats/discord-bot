package com.outlasttrialsstats.discordbot.feature.widget.service;

import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import com.outlasttrialsstats.discordbot.feature.widget.WidgetFields;
import com.outlasttrialsstats.discordbot.feature.widget.dto.WidgetDynamicField;
import com.outlasttrialsstats.discordbot.feature.widget.dto.WidgetProfilePayload;
import com.outlasttrialsstats.discordbot.shared.EnumFormatter;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WidgetFieldMapper {

    public WidgetProfilePayload toPayload(String discordUserId, DiscordProfileResponse profile) {
        String username = profile.getDisplayName() != null ? profile.getDisplayName() : discordUserId;

        List<WidgetDynamicField> fields = new ArrayList<>();
        addNumber(fields, WidgetFields.PRESTIGE, profile.getPrestigeLevel());
        addNumber(fields, WidgetFields.LEVEL, profile.getLevel());
        addNumber(fields, WidgetFields.COMPLETED_TRIALS, profile.getCompletedTrials());
        if (profile.getTrialsInHours() != null) {
            fields.add(WidgetDynamicField.ofNumber(WidgetFields.TRIALS_IN_HOURS,
                    profile.getTrialsInHours().setScale(1, RoundingMode.HALF_UP)));
        }
        addNumber(fields, WidgetFields.DEATHS, profile.getDeaths());
        addNumber(fields, WidgetFields.REAGENT_RELEASES, profile.getReagentReleases());
        if (profile.getInvasionRanking() != null) {
            fields.add(WidgetDynamicField.ofString(WidgetFields.INVASION_RANK,
                    EnumFormatter.titleCase(profile.getInvasionRanking().getValue())));
        }
        if (profile.getPlatformType() != null) {
            fields.add(WidgetDynamicField.ofString(WidgetFields.PLATFORM,
                    EnumFormatter.titleCase(profile.getPlatformType().getValue())));
        }

        return new WidgetProfilePayload(username, fields);
    }

    private static void addNumber(List<WidgetDynamicField> fields, String name, Integer value) {
        if (value != null) {
            fields.add(WidgetDynamicField.ofNumber(name, value));
        }
    }
}
