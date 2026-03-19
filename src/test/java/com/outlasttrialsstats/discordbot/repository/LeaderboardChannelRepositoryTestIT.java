package com.outlasttrialsstats.discordbot.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.IntegrationTest;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class LeaderboardChannelRepositoryTestIT {

    @Autowired
    private LeaderboardChannelRepository repository;

    private static final String GUILD_ID = "guild-1";
    private static final String OTHER_GUILD_ID = "guild-2";

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findByGuildId_returnsMatchingBindings() {
        repository.save(new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "channel-1", List.of("msg-1"), 1));
        repository.save(new LeaderboardChannel(GUILD_ID, StatisticType.DEATHS, "channel-1", List.of("msg-2"), 1));
        repository.save(new LeaderboardChannel(OTHER_GUILD_ID, StatisticType.PRESTIGE, "channel-2", List.of("msg-3"), 1));

        var result = repository.findByGuildId(GUILD_ID);

        assertThat(result)
                .hasSize(2)
                .extracting(LeaderboardChannel::getCategory)
                .containsExactlyInAnyOrder(StatisticType.PRESTIGE, StatisticType.DEATHS);
    }

    @Test
    void findByGuildIdAndCategory_findsExact() {
        repository.save(new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "channel-1", List.of("msg-1"), 1));
        repository.save(new LeaderboardChannel(GUILD_ID, StatisticType.DEATHS, "channel-1", List.of("msg-2"), 1));

        var result = repository.findByGuildIdAndCategory(GUILD_ID, StatisticType.PRESTIGE);

        assertThat(result).isPresent();
        assertThat(result.get().getChannelId()).isEqualTo("channel-1");
    }

    @Test
    void findByGuildIdAndCategory_noMatch_returnsEmpty() {
        repository.save(new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "channel-1", List.of("msg-1"), 1));

        var result = repository.findByGuildIdAndCategory(GUILD_ID, StatisticType.DEATHS);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllWithMessageIds_loadsMessageIds() {
        repository.save(new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "channel-1", List.of("msg-1", "msg-2"), 2));
        repository.save(new LeaderboardChannel(OTHER_GUILD_ID, StatisticType.DEATHS, "channel-2", List.of("msg-3"), 1));

        var result = repository.findAllWithMessageIds();

        assertThat(result).hasSize(2);

        var prestigeBinding = result.stream()
                .filter(lc -> lc.getCategory() == StatisticType.PRESTIGE)
                .findFirst().orElseThrow();
        assertThat(prestigeBinding.getMessageIds()).containsExactly("msg-1", "msg-2");
    }

    @Test
    void findByMessageId_findsBindingContainingMessage() {
        repository.save(new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "channel-1", List.of("msg-1", "msg-2"), 2));

        var result = repository.findByMessageId("msg-2");

        assertThat(result).isPresent();
        assertThat(result.get().getCategory()).isEqualTo(StatisticType.PRESTIGE);
        assertThat(result.get().getGuildId()).isEqualTo(GUILD_ID);
    }

    @Test
    void findByMessageId_noMatch_returnsEmpty() {
        repository.save(new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "channel-1", List.of("msg-1"), 1));

        var result = repository.findByMessageId("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void messageIds_preservesOrder() {
        List<String> orderedIds = List.of("msg-3", "msg-1", "msg-2");
        repository.save(new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "channel-1", orderedIds, 3));

        var result = repository.findByGuildIdAndCategory(GUILD_ID, StatisticType.PRESTIGE);

        assertThat(result).isPresent();
        assertThat(result.get().getMessageIds()).containsExactly("msg-3", "msg-1", "msg-2");
    }
}
