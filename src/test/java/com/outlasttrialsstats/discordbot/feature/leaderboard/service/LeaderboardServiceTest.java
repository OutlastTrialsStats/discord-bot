package com.outlasttrialsstats.discordbot.feature.leaderboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardEntry;
import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import com.outlasttrialsstats.discordbot.repository.LeaderboardChannelRepository;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.concurrent.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
                GUILD_ID, guild, StatisticType.PRESTIGE, response, true, false, false);

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
                GUILD_ID, guild, StatisticType.PRESTIGE, response, true, false, false);

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
                GUILD_ID, guild, StatisticType.PRESTIGE, response, true, true, true);

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
                GUILD_ID, guild, StatisticType.PRESTIGE, response, true, true, true);

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
                GUILD_ID, guild, StatisticType.PRESTIGE, response, true, false, false);

        assertThat(embed.getDescription()).contains("...");
    }

    @Test
    void buildLeaderboardEmbed_entryWithDiscordUser_showsMention() {
        stubMentionMessages();
        Task<List<Member>> task = stubMembersTask();
        Member member = member("111111111111111111");
        when(task.get()).thenReturn(List.of(member));

        DiscordLeaderboardEntry entry = createEntry("Player", 1, 100);
        entry.discordUserId("111111111111111111");
        DiscordLeaderboardResponse response = createResponse(List.of(entry), 1, 1, 1);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, guild, StatisticType.PRESTIGE, response, true, false, false);

        assertThat(embed.getDescription()).contains("<@111111111111111111>");
        verify(guild, never()).getMemberById(anyString());
    }

    @Test
    void buildLeaderboardEmbed_multipleDiscordUsers_resolvesWithSingleRequest() {
        stubMentionMessages();
        Task<List<Member>> task = stubMembersTask();
        Member member = member("111111111111111111");
        when(task.get()).thenReturn(List.of(member));

        DiscordLeaderboardEntry inGuild = createEntry("InGuild", 1, 100);
        inGuild.discordUserId("111111111111111111");
        DiscordLeaderboardEntry notInGuild = createEntry("NotInGuild", 2, 90);
        notInGuild.discordUserId("222222222222222222");
        DiscordLeaderboardEntry noDiscord = createEntry("NoDiscord", 3, 80);
        DiscordLeaderboardResponse response = createResponse(List.of(inGuild, notInGuild, noDiscord), 1, 1, 3);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, guild, StatisticType.PRESTIGE, response, true, false, false);

        ArgumentCaptor<long[]> idsCaptor = ArgumentCaptor.forClass(long[].class);
        verify(guild, times(1)).retrieveMembersByIds(eq(false), idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(111111111111111111L, 222222222222222222L);
        assertThat(embed.getDescription()).contains("<@111111111111111111>");
        assertThat(embed.getDescription()).doesNotContain("<@222222222222222222>");
    }

    @Test
    void buildLeaderboardEmbed_moreThanChunkSize_splitsRequests() {
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.category.PRESTIGE")))
                .thenReturn("Prestige");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.title"), any()))
                .thenReturn("Prestige Leaderboard");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.entry"), any(), any(), any(), any(), any()))
                .thenReturn("x");
        Task<List<Member>> task = stubMembersTask();
        when(task.get()).thenReturn(List.of());

        List<DiscordLeaderboardEntry> entries = new ArrayList<>();
        for (int i = 1; i <= LeaderboardService.MEMBER_CHUNK_SIZE + 1; i++) {
            DiscordLeaderboardEntry entry = createEntry("P" + i, i, 1);
            entry.discordUserId(String.valueOf(100000000000000000L + i));
            entries.add(entry);
        }
        DiscordLeaderboardResponse response = createResponse(entries, 1, 1, entries.size());

        leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, guild, StatisticType.PRESTIGE, response, true, false, false);

        verify(guild, times(2)).retrieveMembersByIds(eq(false), any(long[].class));
    }

    @Test
    void buildLeaderboardEmbed_retrieveFails_fallsBackToCache() {
        stubMentionMessages();
        Task<List<Member>> task = stubMembersTask();
        when(task.get()).thenThrow(new RuntimeException("timeout"));
        when(guild.getMemberById("111111111111111111")).thenReturn(mock(Member.class));

        DiscordLeaderboardEntry entry = createEntry("Player", 1, 100);
        entry.discordUserId("111111111111111111");
        DiscordLeaderboardResponse response = createResponse(List.of(entry), 1, 1, 1);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, guild, StatisticType.PRESTIGE, response, true, false, false);

        assertThat(embed.getDescription()).contains("<@111111111111111111>");
    }

    @Test
    void buildLeaderboardEmbed_invalidDiscordUserId_noLookupAndNoMention() {
        stubMentionMessages();

        DiscordLeaderboardEntry entry = createEntry("Player", 1, 100);
        entry.discordUserId("discord-123");
        DiscordLeaderboardResponse response = createResponse(List.of(entry), 1, 1, 1);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, guild, StatisticType.PRESTIGE, response, true, false, false);

        verify(guild, never()).retrieveMembersByIds(anyBoolean(), any(long[].class));
        assertThat(embed.getDescription()).doesNotContain("<@");
    }

    @Test
    void buildLeaderboardEmbed_nullGuild_noMention() {
        stubMentionMessages();

        DiscordLeaderboardEntry entry = createEntry("Player", 1, 100);
        entry.discordUserId("111111111111111111");
        DiscordLeaderboardResponse response = createResponse(List.of(entry), 1, 1, 1);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, null, StatisticType.PRESTIGE, response, true, false, false);

        assertThat(embed.getDescription()).doesNotContain("<@");
    }

    private void stubMentionMessages() {
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.category.PRESTIGE")))
                .thenReturn("Prestige");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.title"), any()))
                .thenReturn("Prestige Leaderboard");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.entry"), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(4).toString()
                        + invocation.getArgument(5).toString());
    }

    @SuppressWarnings("unchecked")
    private Task<List<Member>> stubMembersTask() {
        Task<List<Member>> task = mock(Task.class);
        when(guild.retrieveMembersByIds(eq(false), any(long[].class))).thenReturn(task);
        when(task.setTimeout(any(Duration.class))).thenReturn(task);
        return task;
    }

    private static Member member(String id) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        return member;
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
                GUILD_ID, guild, StatisticType.PRESTIGE, response, true, false, false);

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
                GUILD_ID, guild, StatisticType.PRESTIGE, response, true, false, false);

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
                GUILD_ID, guild, StatisticType.PRESTIGE, response, true, false, false);

        assertThat(embed.getDescription()).contains("Player");
        assertThat(embed.getDescription()).doesNotContain("outlasttrialsstats.com");
    }

    @Test
    void buildLeaderboardEmbed_fiftyLongEntries_staysWithinDiscordLimits() {
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.category.PRESTIGE")))
                .thenReturn("Prestige");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.title"), any()))
                .thenReturn("Prestige Leaderboard");
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.footer"), any(), any(), any()))
                .thenReturn("F".repeat(MessageEmbed.TEXT_MAX_LENGTH));
        // A custom guild template can make every line long; the service must still respect the limits.
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.entry"), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> "#" + invocation.getArgument(3) + " **" + invocation.getArgument(4)
                        + "**" + "x".repeat(150));
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.truncated"), any()))
                .thenAnswer(invocation -> "\u2026 and " + invocation.getArgument(2) + " more");

        List<DiscordLeaderboardEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            entries.add(createEntry("[*_~|`\\" + "\uD83D\uDE00".repeat(60) + i, i, i));
        }
        DiscordLeaderboardResponse response = createResponse(entries, 1, 1, 50);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, null, StatisticType.PRESTIGE, response, true, true, true);

        String description = embed.getDescription();
        assertThat(description.length()).isLessThanOrEqualTo(MessageEmbed.DESCRIPTION_MAX_LENGTH);
        assertThat(embed.getLength()).isLessThanOrEqualTo(MessageEmbed.EMBED_MAX_LENGTH_BOT);
        assertThat(description).contains("#1 **").endsWith(" more");
        assertThat(description).doesNotContain("#50 **");
        assertThat(embed.isSendable()).isTrue();
    }

    @Test
    void buildLeaderboardEmbed_tooManyLinesWithoutTruncationMessage_usesEllipsisFallback() {
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.entry"), any(), any(), any(), any(), any()))
                .thenReturn("y".repeat(1000));

        List<DiscordLeaderboardEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            entries.add(createEntry("P" + i, i, i));
        }
        DiscordLeaderboardResponse response = createResponse(entries, 1, 1, 10);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, null, StatisticType.PRESTIGE, response, false, false, false);

        assertThat(embed.getDescription()).hasSizeLessThanOrEqualTo(MessageEmbed.DESCRIPTION_MAX_LENGTH)
                .endsWith("\u2026");
    }

    @Test
    void buildLeaderboardEmbed_longNameWithEmoji_truncatesByCodePoints() {
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.entry"), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(4).toString());

        DiscordLeaderboardEntry entry = createEntry("\uD83D\uDE00".repeat(25), 1, 1);
        entry.setProfileId(null);
        DiscordLeaderboardResponse response = createResponse(List.of(entry), 1, 1, 1);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, null, StatisticType.PRESTIGE, response, false, false, false);

        assertThat(embed.getDescription().strip()).isEqualTo("\uD83D\uDE00".repeat(17) + "...");
    }

    @Test
    void buildLeaderboardEmbed_largeRanking_isPassedUngroupedSoPaddingStaysAligned() {
        when(messageService.getMessage(eq(GUILD_ID), eq("leaderboard.entry"), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2) + "|" + invocation.getArgument(3));

        DiscordLeaderboardResponse response = createResponse(
                List.of(createEntry("A", 1234, 1), createEntry("B", 99, 1)), 1, 1, 2);

        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                GUILD_ID, null, StatisticType.PRESTIGE, response, false, false, false);

        assertThat(embed.getDescription()).contains("|1234\n").contains("  |99\n");
    }

    @Test
    void escapeName_escapesMarkdownAndLinkBrackets() {
        assertThat(LeaderboardService.escapeName("[a](b) *c* _d_ `e` ||f|| ~~g~~"))
                .isEqualTo("\\[a\\](b) \\*c\\* \\_d\\_ \\`e\\` \\|\\|f\\|\\| \\~\\~g\\~\\~");
        assertThat(LeaderboardService.escapeName("name\\")).isEqualTo("name\\\\");
    }

    @Test
    void saveBinding_newBinding_savesToRepository() {
        when(leaderboardChannelRepository.findByGuildIdAndCategory(GUILD_ID, StatisticType.PRESTIGE))
                .thenReturn(Optional.empty());

        Optional<LeaderboardBinding> previous = leaderboardService.saveBinding(
                GUILD_ID, StatisticType.PRESTIGE, "channel-1", List.of("msg-1", "msg-2"), 2);

        assertThat(previous).isEmpty();
        verify(leaderboardChannelRepository).save(any(LeaderboardChannel.class));
    }

    @Test
    void saveBinding_existingBinding_updatesExistingAndReturnsPreviousState() {
        LeaderboardChannel existing = new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "old-channel", List.of("old-msg"), 1);
        existing.setId(7L);
        when(leaderboardChannelRepository.findByGuildIdAndCategory(GUILD_ID, StatisticType.PRESTIGE))
                .thenReturn(Optional.of(existing));

        Optional<LeaderboardBinding> previous = leaderboardService.saveBinding(
                GUILD_ID, StatisticType.PRESTIGE, "new-channel", List.of("new-msg"), 3);

        assertThat(existing.getChannelId()).isEqualTo("new-channel");
        assertThat(existing.getMessageIds()).containsExactly("new-msg");
        assertThat(existing.getMaxPages()).isEqualTo(3);
        verify(leaderboardChannelRepository).save(existing);
        assertThat(previous).contains(new LeaderboardBinding(
                7L, GUILD_ID, StatisticType.PRESTIGE, "old-channel", List.of("old-msg"), 1));
    }

    @Test
    void removeBinding_exists_deletesAndReturnsDetachedSnapshot() {
        LeaderboardChannel binding = new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "channel-1", List.of("msg-1"), 1);
        when(leaderboardChannelRepository.findByGuildIdAndCategory(GUILD_ID, StatisticType.PRESTIGE))
                .thenReturn(Optional.of(binding));

        Optional<LeaderboardBinding> result = leaderboardService.removeBinding(GUILD_ID, StatisticType.PRESTIGE);
        binding.getMessageIds().clear();

        assertThat(result).isPresent();
        assertThat(result.get().messageIds()).containsExactly("msg-1");
        assertThat(result.get().channelId()).isEqualTo("channel-1");
        verify(leaderboardChannelRepository).delete(binding);
    }

    @Test
    void removeBinding_notExists_returnsEmpty() {
        when(leaderboardChannelRepository.findByGuildIdAndCategory(GUILD_ID, StatisticType.PRESTIGE))
                .thenReturn(Optional.empty());

        Optional<LeaderboardBinding> result = leaderboardService.removeBinding(GUILD_ID, StatisticType.PRESTIGE);

        assertThat(result).isEmpty();
        verify(leaderboardChannelRepository, never()).delete(any());
    }

    @Test
    void removeBindingIfContainsMessage_messageStillBound_deletes() {
        LeaderboardChannel binding = new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "channel-1", List.of("msg-1", "msg-2"), 2);
        when(leaderboardChannelRepository.findById(7L)).thenReturn(Optional.of(binding));

        Optional<LeaderboardBinding> result = leaderboardService.removeBindingIfContainsMessage(7L, "msg-2");

        assertThat(result).isPresent();
        verify(leaderboardChannelRepository).delete(binding);
    }

    @Test
    void removeBindingIfContainsMessage_bindingWasReplaced_keepsIt() {
        LeaderboardChannel binding = new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "channel-1", List.of("new-msg"), 1);
        when(leaderboardChannelRepository.findById(7L)).thenReturn(Optional.of(binding));

        Optional<LeaderboardBinding> result = leaderboardService.removeBindingIfContainsMessage(7L, "old-msg");

        assertThat(result).isEmpty();
        verify(leaderboardChannelRepository, never()).delete(any());
    }

    @Test
    void removeBindingIfContainsMessage_nullId_doesNothing() {
        assertThat(leaderboardService.removeBindingIfContainsMessage(null, "msg-1")).isEmpty();
        verify(leaderboardChannelRepository, never()).findById(any());
    }

    @Test
    void removeBindingIfInChannel_channelChanged_keepsIt() {
        LeaderboardChannel binding = new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "new-channel", List.of("msg-1"), 1);
        when(leaderboardChannelRepository.findById(7L)).thenReturn(Optional.of(binding));

        assertThat(leaderboardService.removeBindingIfInChannel(7L, "old-channel")).isEmpty();
        verify(leaderboardChannelRepository, never()).delete(any());
    }

    @Test
    void removeBindingIfInChannel_sameChannel_deletes() {
        LeaderboardChannel binding = new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "channel-1", List.of("msg-1"), 1);
        when(leaderboardChannelRepository.findById(7L)).thenReturn(Optional.of(binding));

        assertThat(leaderboardService.removeBindingIfInChannel(7L, "channel-1")).isPresent();
        verify(leaderboardChannelRepository).delete(binding);
    }

    @Test
    void removeBindingsByMessageIds_deletesAllMatches() {
        LeaderboardChannel first = new LeaderboardChannel(GUILD_ID, StatisticType.PRESTIGE, "channel-1", List.of("msg-1"), 1);
        LeaderboardChannel second = new LeaderboardChannel(GUILD_ID, StatisticType.DEATHS, "channel-1", List.of("msg-2"), 1);
        when(leaderboardChannelRepository.findByMessageIdIn(List.of("msg-1", "msg-2")))
                .thenReturn(List.of(first, second));

        List<LeaderboardBinding> result = leaderboardService.removeBindingsByMessageIds(List.of("msg-1", "msg-2"));

        assertThat(result).extracting(LeaderboardBinding::category)
                .containsExactly(StatisticType.PRESTIGE, StatisticType.DEATHS);
        verify(leaderboardChannelRepository).delete(first);
        verify(leaderboardChannelRepository).delete(second);
    }

    @Test
    void removeBindingsByMessageIds_empty_skipsQuery() {
        assertThat(leaderboardService.removeBindingsByMessageIds(List.of())).isEmpty();
        verify(leaderboardChannelRepository, never()).findByMessageIdIn(any());
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
