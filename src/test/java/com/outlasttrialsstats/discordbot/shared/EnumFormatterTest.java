package com.outlasttrialsstats.discordbot.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnumFormatterTest {

    @Test
    void titleCase_formatsUnderscoreValues() {
        assertThat(EnumFormatter.titleCase("GOLD_1")).isEqualTo("Gold 1");
        assertThat(EnumFormatter.titleCase("UNRANKED")).isEqualTo("Unranked");
        assertThat(EnumFormatter.titleCase("EPIC_GAMES")).isEqualTo("Epic Games");
        assertThat(EnumFormatter.titleCase("COMPLETED_TRIALS")).isEqualTo("Completed Trials");
    }
}
