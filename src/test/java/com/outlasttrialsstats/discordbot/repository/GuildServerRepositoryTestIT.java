package com.outlasttrialsstats.discordbot.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.outlasttrialsstats.discordbot.IntegrationTest;
import com.outlasttrialsstats.discordbot.entity.GuildServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class GuildServerRepositoryTestIT {

    @Autowired
    private GuildServerRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void newServer_isPresentByDefault() {
        repository.save(new GuildServer("1"));

        assertThat(repository.findById("1").orElseThrow().isBotPresent()).isTrue();
    }

    @Test
    void findGuildIdsWithBotPresent_skipsAbsentGuilds() {
        repository.save(new GuildServer("1"));
        GuildServer left = new GuildServer("2");
        left.setBotPresent(false);
        repository.save(left);

        assertThat(repository.findGuildIdsWithBotPresent()).containsExactly("1");
    }

    @Test
    void markBotAbsent_keepsTheOtherData() {
        GuildServer server = new GuildServer("1");
        server.setMemberCount(5);
        server.setInviteUrl("https://discord.gg/a");
        repository.save(server);

        repository.markBotAbsent("1");

        GuildServer reloaded = repository.findById("1").orElseThrow();
        assertThat(reloaded.isBotPresent()).isFalse();
        assertThat(reloaded.getMemberCount()).isEqualTo(5);
        assertThat(reloaded.getInviteUrl()).isEqualTo("https://discord.gg/a");
    }
}
