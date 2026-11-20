package com.outlasttrialsstats.discordbot.feature.setup.panel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PanelActionTest {

    @Test
    void idAndParse_roundTrip() {
        String id = PanelAction.id("roles", "add", "PRESTIGE");

        assertThat(id).isEqualTo("setup:panel:roles:add:PRESTIGE");
        assertThat(PanelAction.parse(id)).contains(new PanelAction("roles", "add", List.of("PRESTIGE")));
    }

    @Test
    void parse_withoutArgs() {
        assertThat(PanelAction.parse(PanelAction.nav("event")))
                .contains(new PanelAction("event", PanelAction.VIEW, List.of()));
    }

    @Test
    void parse_foreignOrIncompleteIds_areIgnored() {
        assertThat(PanelAction.matches("leaderboard:next:DEATHS:2")).isFalse();
        assertThat(PanelAction.parse("leaderboard:next:DEATHS:2")).isEmpty();
        assertThat(PanelAction.parse("setup:panel:roles")).isEmpty();
        assertThat(PanelAction.parse(null)).isEmpty();
    }

    @Test
    void arg_outOfRange_isNull() {
        PanelAction action = PanelAction.parse("setup:panel:roles:add").orElseThrow();

        assertThat(action.arg(0)).isNull();
    }
}
