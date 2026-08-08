package com.outlasttrialsstats.discordbot.feature.widget.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.outlasttrialsstats.backend.api.model.DiscordProfileResponse;
import com.outlasttrialsstats.backend.api.model.InvasionRanking;
import com.outlasttrialsstats.backend.api.model.PlatformType;
import com.outlasttrialsstats.discordbot.feature.widget.WidgetFields;
import com.outlasttrialsstats.discordbot.feature.widget.dto.WidgetDynamicField;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WidgetFieldMapperTest {

    private final WidgetFieldMapper mapper = new WidgetFieldMapper();

    @Test
    void toPayload_mapsAllAvailableFields() {
        var profile = new DiscordProfileResponse();
        profile.setDisplayName("TestPlayer");
        profile.setPrestigeLevel(7);
        profile.setLevel(45);
        profile.setCompletedTrials(321);
        profile.setTrialsInHours(new BigDecimal("123.456"));
        profile.setDeaths(99);
        profile.setReagentReleases(12);
        profile.setInvasionRanking(InvasionRanking.GOLD_1);
        profile.setPlatformType(PlatformType.STEAM);

        var payload = mapper.toPayload("user-1", profile);

        assertThat(payload.username()).isEqualTo("TestPlayer");
        assertThat(payload.fields()).containsExactly(
                WidgetDynamicField.ofNumber(WidgetFields.PRESTIGE, 7),
                WidgetDynamicField.ofNumber(WidgetFields.LEVEL, 45),
                WidgetDynamicField.ofNumber(WidgetFields.COMPLETED_TRIALS, 321),
                WidgetDynamicField.ofNumber(WidgetFields.TRIALS_IN_HOURS, new BigDecimal("123.5")),
                WidgetDynamicField.ofNumber(WidgetFields.DEATHS, 99),
                WidgetDynamicField.ofNumber(WidgetFields.REAGENT_RELEASES, 12),
                WidgetDynamicField.ofString(WidgetFields.INVASION_RANK, "Gold 1"),
                WidgetDynamicField.ofString(WidgetFields.PLATFORM, "Steam"));
    }

    @Test
    void toPayload_skipsNullFields() {
        var profile = new DiscordProfileResponse();
        profile.setDisplayName("TestPlayer");
        profile.setPrestigeLevel(3);

        var payload = mapper.toPayload("user-1", profile);

        assertThat(payload.fields()).containsExactly(
                WidgetDynamicField.ofNumber(WidgetFields.PRESTIGE, 3));
    }

    @Test
    void toPayload_missingDisplayName_fallsBackToDiscordUserId() {
        var payload = mapper.toPayload("user-1", new DiscordProfileResponse());

        assertThat(payload.username()).isEqualTo("user-1");
        assertThat(payload.fields()).isEmpty();
    }

    @Test
    void toRequestBody_buildsDiscordStructure() {
        var profile = new DiscordProfileResponse();
        profile.setDisplayName("TestPlayer");
        profile.setLevel(10);

        var body = mapper.toPayload("user-1", profile).toRequestBody();

        assertThat(body).containsEntry("username", "TestPlayer");
        assertThat(body.get("data")).isEqualTo(
                Map.of("dynamic", java.util.List.of(WidgetDynamicField.ofNumber(WidgetFields.LEVEL, 10))));
    }

}
