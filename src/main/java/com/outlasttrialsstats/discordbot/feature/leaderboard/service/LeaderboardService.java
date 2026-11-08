package com.outlasttrialsstats.discordbot.feature.leaderboard.service;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardEntry;
import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import com.outlasttrialsstats.discordbot.repository.LeaderboardChannelRepository;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class LeaderboardService {

    /** Discord allows at most 100 user ids per member chunk request. */
    static final int MEMBER_CHUNK_SIZE = 100;
    private static final Duration MEMBER_RETRIEVE_TIMEOUT = Duration.ofSeconds(5);

    private final TOTStatsApiClient apiClient;
    private final LeaderboardChannelRepository leaderboardChannelRepository;
    private final MessageService messageService;

    public Optional<DiscordLeaderboardResponse> fetchLeaderboard(StatisticType category, int page) {
        return apiClient.getLeaderboard(category, page);
    }

    public MessageEmbed buildLeaderboardEmbed(String guildId, Guild guild, StatisticType category,
                                               DiscordLeaderboardResponse response, boolean includeTitle,
                                               boolean includeFooter, boolean includeTotalEntries) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(0x5865F2);

        if (includeTitle) {
            String categoryName = getCategoryDisplayName(guildId, category);
            embed.setTitle(messageService.getMessage(guildId, "leaderboard.title", categoryName));
        }

        List<DiscordLeaderboardEntry> entries = response.getResults();
        if (entries == null || entries.isEmpty()) {
            embed.setDescription(messageService.getMessage(guildId, "leaderboard.empty"));
        } else {
            int maxRankWidth = entries.stream()
                    .mapToInt(e -> e.getRanking() != null ? String.valueOf(e.getRanking()).length() : 1)
                    .max().orElse(1);

            Set<String> guildMemberIds = resolveGuildMemberIds(guild, entries);

            StringBuilder description = new StringBuilder();
            for (DiscordLeaderboardEntry entry : entries) {
                description.append(formatEntry(guildId, entry, guildMemberIds, maxRankWidth));
            }
            embed.setDescription(description.toString());
        }

        if (includeFooter) {
            Integer currentPage = response.getCurrentPage();
            Integer totalPages = response.getTotalPages();
            if (includeTotalEntries) {
                Integer totalResults = response.getTotalResults();
                embed.setFooter(messageService.getMessage(guildId, "leaderboard.footer",
                        currentPage != null ? currentPage : 1,
                        totalPages != null ? totalPages : 1,
                        totalResults != null ? totalResults : 0));
            } else {
                embed.setFooter(messageService.getMessage(guildId, "leaderboard.footer.compact",
                        currentPage != null ? currentPage : 1,
                        totalPages != null ? totalPages : 1));
            }
        }

        return embed.build();
    }

    /**
     * Resolves which of the page's Discord users are members of the guild. The member cache only holds a
     * handful of members, so the ids are requested from the gateway in one chunk request per 100 ids.
     * This blocks, which is fine because all callers run on scheduler or event-pool threads, never on the
     * gateway thread. On failure the member cache is used as a fallback.
     */
    private Set<String> resolveGuildMemberIds(Guild guild, List<DiscordLeaderboardEntry> entries) {
        if (guild == null) {
            return Set.of();
        }

        Set<String> requestedIds = new LinkedHashSet<>();
        for (DiscordLeaderboardEntry entry : entries) {
            String discordUserId = getDiscordUserId(entry);
            if (discordUserId != null && isSnowflake(discordUserId)) {
                requestedIds.add(discordUserId);
            }
        }
        if (requestedIds.isEmpty()) {
            return Set.of();
        }

        List<String> idList = new ArrayList<>(requestedIds);
        Set<String> memberIds = new HashSet<>();
        for (int start = 0; start < idList.size(); start += MEMBER_CHUNK_SIZE) {
            List<String> batch = idList.subList(start, Math.min(start + MEMBER_CHUNK_SIZE, idList.size()));
            long[] ids = batch.stream().mapToLong(Long::parseUnsignedLong).toArray();
            try {
                List<Member> members = guild.retrieveMembersByIds(false, ids)
                        .setTimeout(MEMBER_RETRIEVE_TIMEOUT)
                        .get();
                if (members != null) {
                    members.forEach(member -> memberIds.add(member.getId()));
                }
            } catch (Exception e) {
                log.warn("Failed to retrieve leaderboard members in guild {}, falling back to cache: {}",
                        guild.getId(), e.getMessage());
                for (String id : batch) {
                    if (guild.getMemberById(id) != null) {
                        memberIds.add(id);
                    }
                }
            }
        }
        return memberIds;
    }

    private static String getDiscordUserId(DiscordLeaderboardEntry entry) {
        return entry.getDiscordUserId() != null && entry.getDiscordUserId().isPresent()
                ? entry.getDiscordUserId().get() : null;
    }

    private static boolean isSnowflake(String id) {
        try {
            Long.parseUnsignedLong(id);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String formatEntry(String guildId, DiscordLeaderboardEntry entry, Set<String> guildMemberIds,
                               int maxRankWidth) {
        String rawName = entry.getDisplayName() != null
                ? entry.getDisplayName().replaceAll("[\\p{C}]", "").strip()
                : "Unknown";
        if (rawName.isBlank()) {
            rawName = "Unknown";
        }
        if (rawName.length() > 20) {
            rawName = rawName.substring(0, 17) + "...";
        }

        String displayName = entry.getProfileId() != null
                ? "[" + rawName + "](https://outlasttrialsstats.com/profile/" + entry.getProfileId() + ")"
                : rawName;

        String mention = "";
        String discordUserId = getDiscordUserId(entry);
        if (discordUserId != null && guildMemberIds.contains(discordUserId)) {
            mention = " (<@" + discordUserId + ">)";
        }

        int ranking = entry.getRanking() != null ? entry.getRanking() : 0;
        int value = entry.getValue() != null ? entry.getValue() : 0;
        String rankPadding = " ".repeat(maxRankWidth - String.valueOf(ranking).length());
        String formattedValue = String.format("%,d", value);

        return messageService.getMessage(guildId, "leaderboard.entry",
                rankPadding, ranking, displayName, mention, formattedValue) + "\n";
    }

    @Transactional
    public void saveBinding(String guildId, StatisticType category, String channelId, List<String> messageIds, int maxPages) {
        Optional<LeaderboardChannel> existing = leaderboardChannelRepository
                .findByGuildIdAndCategory(guildId, category);

        if (existing.isPresent()) {
            LeaderboardChannel binding = existing.get();
            binding.setChannelId(channelId);
            binding.setMessageIds(new ArrayList<>(messageIds));
            binding.setMaxPages(maxPages);
            leaderboardChannelRepository.save(binding);
        } else {
            leaderboardChannelRepository.save(new LeaderboardChannel(guildId, category, channelId, messageIds, maxPages));
        }
    }

    @Transactional
    public Optional<LeaderboardChannel> removeBinding(String guildId, StatisticType category) {
        Optional<LeaderboardChannel> existing = leaderboardChannelRepository
                .findByGuildIdAndCategory(guildId, category);
        existing.ifPresent(leaderboardChannelRepository::delete);
        return existing;
    }

    public List<LeaderboardChannel> getAllBindings() {
        return leaderboardChannelRepository.findAllWithMessageIds();
    }

    public String getCategoryDisplayName(String guildId, StatisticType category) {
        return messageService.getMessage(guildId, "leaderboard.category." + category.getValue());
    }
}
