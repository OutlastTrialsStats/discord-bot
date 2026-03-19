package com.outlasttrialsstats.discordbot.feature.leaderboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardEntry;
import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import com.outlasttrialsstats.discordbot.repository.LeaderboardChannelRepository;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock
    private TOTStatsApiClient apiClient;

    @Mock
    private LeaderboardChannelRepository leaderboardChannelRepository;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private LeaderboardService leaderboardService;

    @Mock
    private Guild guild;

    private static final String GUILD_ID = "guild-1";

    @Test
    void buildLeaderboardEmbed_withEntries_showsFormattedEntries() {
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.category.PRESTIGE")))
                .thenReturn("Prestige");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.title"), eq("Prestige")))
                .thenReturn("Prestige Leaderboard");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.entry"), any(), any(), any(), any(), any()))
                .thenReturn("#1 **Player** — `100`");

        DiscordLeaderboardResponse response = createResponse(
                List.of(createEntry("Player", 1, 100)), 1, 1, 1);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, guild, StatisticType.PRESTIGE, response, false);

        assertThat(embed.getTitle()).isEqualTo("Prestige Leaderboard");
        assertThat(embed.getDescription()).contains("#1 **Player** — `100`");
        assertThat(embed.getFooter()).isNull();
    }

    @Test
    void buildLeaderboardEmbed_emptyEntries_showsEmptyMessage() {
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.category.PRESTIGE")))
                .thenReturn("Prestige");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.title"), eq("Prestige")))
                .thenReturn("Prestige Leaderboard");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.empty")))
                .thenReturn("No entries found.");

        DiscordLeaderboardResponse response = createResponse(List.of(), 1, 1, 0);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, guild, StatisticType.PRESTIGE, response, false);

        assertThat(embed.getDescription()).isEqualTo("No entries found.");
    }

    @Test
    void buildLeaderboardEmbed_includeFooter_showsPageInfo() {
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.category.PRESTIGE")))
                .thenReturn("Prestige");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.title"), eq("Prestige")))
                .thenReturn("Prestige Leaderboard");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.empty")))
                .thenReturn("No entries found.");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.footer"), eq(2), eq(5), eq(50)))
                .thenReturn("Page 2/5 | 50 total entries");

        DiscordLeaderboardResponse response = createResponse(List.of(), 2, 5, 50);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, guild, StatisticType.PRESTIGE, response, true);

        assertThat(embed.getFooter()).isNotNull();
        assertThat(embed.getFooter().getText()).isEqualTo("Page 2/5 | 50 total entries");
    }

    @Test
    void buildLeaderboardEmbed_nullResponseFields_usesDefaults() {
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.category.PRESTIGE")))
                .thenReturn("Prestige");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.title"), eq("Prestige")))
                .thenReturn("Prestige Leaderboard");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.empty")))
                .thenReturn("No entries found.");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.footer"), eq(1), eq(1), eq(0)))
                .thenReturn("Page 1/1 | 0 total entries");

        DiscordLeaderboardResponse response = createResponse(List.of(), null, null, null);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, guild, StatisticType.PRESTIGE, response, true);

        assertThat(embed.getFooter().getText()).isEqualTo("Page 1/1 | 0 total entries");
    }

    @Test
    void buildLeaderboardEmbed_entryWithLongName_isTruncated() {
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.category.PRESTIGE")))
                .thenReturn("Prestige");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.title"), any()))
                .thenReturn("Prestige Leaderboard");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.entry"), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String displayName = invocation.getArgument(4).toString();
                    return "#1 **" + displayName + "** — `100`";
                });

        DiscordLeaderboardEntry entry = createEntry("ThisIsAVeryLongPlayerNameThatExceeds", 1, 100);
        DiscordLeaderboardResponse response = createResponse(List.of(entry), 1, 1, 1);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, guild, StatisticType.PRESTIGE, response, false);

        assertThat(embed.getDescription()).contains("...");
    }

    @Test
    void buildLeaderboardEmbed_entryWithDiscordUser_showsMention() {
        when(guild.getMemberById("discord-123")).thenReturn(mock(Member.class));
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.category.PRESTIGE")))
                .thenReturn("Prestige");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.title"), any()))
                .thenReturn("Prestige Leaderboard");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.entry"), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String mention = invocation.getArgument(5).toString();
                    return "#1 **Player**" + mention + " — `100`";
                });

        DiscordLeaderboardEntry entry = createEntry("Player", 1, 100);
        entry.discordUserId("discord-123");
        DiscordLeaderboardResponse response = createResponse(List.of(entry), 1, 1, 1);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, guild, StatisticType.PRESTIGE, response, false);

        assertThat(embed.getDescription()).contains("<@discord-123>");
    }

    @Test
    void buildLeaderboardEmbed_entryWithNullDisplayName_showsUnknown() {
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.category.PRESTIGE")))
                .thenReturn("Prestige");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.title"), any()))
                .thenReturn("Prestige Leaderboard");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.entry"), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String displayName = invocation.getArgument(4).toString();
                    return "#1 **" + displayName + "** — `0`";
                });

        DiscordLeaderboardEntry entry = createEntry(null, 1, 0);
        DiscordLeaderboardResponse response = createResponse(List.of(entry), 1, 1, 1);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, guild, StatisticType.PRESTIGE, response, false);

        assertThat(embed.getDescription()).contains("Unknown");
    }

    @Test
    void buildLeaderboardEmbed_entryWithBlankDisplayName_showsUnknown() {
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.category.PRESTIGE")))
                .thenReturn("Prestige");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.title"), any()))
                .thenReturn("Prestige Leaderboard");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.entry"), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String displayName = invocation.getArgument(4).toString();
                    return "#1 **" + displayName + "** — `0`";
                });

        DiscordLeaderboardEntry entry = createEntry("   ", 1, 0);
        DiscordLeaderboardResponse response = createResponse(List.of(entry), 1, 1, 1);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, guild, StatisticType.PRESTIGE, response, false);

        assertThat(embed.getDescription()).contains("Unknown");
    }

    @Test
    void buildLeaderboardEmbed_entryWithoutProfileId_showsPlainName() {
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.category.PRESTIGE")))
                .thenReturn("Prestige");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.title"), any()))
                .thenReturn("Prestige Leaderboard");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.entry"), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String displayName = invocation.getArgument(4).toString();
                    return "#1 **" + displayName + "** — `50`";
                });

        DiscordLeaderboardEntry entry = new DiscordLeaderboardEntry();
        entry.setDisplayName("Player");
        entry.setRanking(1);
        entry.setValue(50);
        // profileId is null — no link should be generated
        DiscordLeaderboardResponse response = createResponse(List.of(entry), 1, 1, 1);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, guild, StatisticType.PRESTIGE, response, false);

        assertThat(embed.getDescription()).contains("Player");
        assertThat(embed.getDescription()).doesNotContain("outlasttrialsstats.com");
    }

    @Test
    void saveBinding_newBinding_savesToRepository() {
        when(leaderboardChannelRepository.findByGuildIdAndCategory(GUILD_ID, StatisticType.PRESTIGE))
                .thenReturn(Optional.empty());

        leaderboardService.saveBinding(GUILD_ID, StatisticType.PRESTIGE, "channel-1", List.of("msg-1", "msg-2"), 2);

        verify(leaderboardChannelRepository).save(any(LeaderboardChannel.class));
    }

    @Test
    void saveBinding_existingBinding_updatesExisting() {
        LeaderboardChannel existing = new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "old-channel", List.of("old-msg"), 1);
        when(leaderboardChannelRepository.findByGuildIdAndCategory(GUILD_ID, StatisticType.PRESTIGE))
                .thenReturn(Optional.of(existing));

        leaderboardService.saveBinding(GUILD_ID, StatisticType.PRESTIGE, "new-channel", List.of("new-msg"), 3);

        assertThat(existing.getChannelId()).isEqualTo("new-channel");
        assertThat(existing.getMessageIds()).containsExactly("new-msg");
        assertThat(existing.getMaxPages()).isEqualTo(3);
        verify(leaderboardChannelRepository).save(existing);
    }

    @Test
    void removeBinding_exists_deletesAndReturns() {
        LeaderboardChannel binding = new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "channel-1", List.of("msg-1"), 1);
        when(leaderboardChannelRepository.findByGuildIdAndCategory(GUILD_ID, StatisticType.PRESTIGE))
                .thenReturn(Optional.of(binding));

        Optional<LeaderboardChannel> result = leaderboardService.removeBinding(GUILD_ID, StatisticType.PRESTIGE);

        assertThat(result).isPresent();
        verify(leaderboardChannelRepository).delete(binding);
    }

    @Test
    void removeBinding_notExists_returnsEmpty() {
        when(leaderboardChannelRepository.findByGuildIdAndCategory(GUILD_ID, StatisticType.PRESTIGE))
                .thenReturn(Optional.empty());

        Optional<LeaderboardChannel> result = leaderboardService.removeBinding(GUILD_ID, StatisticType.PRESTIGE);

        assertThat(result).isEmpty();
        verify(leaderboardChannelRepository, never()).delete(any());
    }

    private DiscordLeaderboardResponse createResponse(List<DiscordLeaderboardEntry> entries,
                                                       Integer currentPage, Integer totalPages, Integer totalResults) {
        DiscordLeaderboardResponse response = new DiscordLeaderboardResponse();
        response.setResults(entries);
        response.setCurrentPage(currentPage);
        response.setTotalPages(totalPages);
        response.setTotalResults(totalResults);
        return response;
    }

    private DiscordLeaderboardEntry createEntry(String displayName, int ranking, int value) {
        DiscordLeaderboardEntry entry = new DiscordLeaderboardEntry();
        entry.setDisplayName(displayName);
        entry.setRanking(ranking);
        entry.setValue(value);
        entry.setProfileId(UUID.randomUUID());
        return entry;
    }
}
