package com.outlasttrialsstats.discordbot.feature.leaderboard.service;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardEntry;
import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import com.outlasttrialsstats.discordbot.repository.LeaderboardChannelRepository;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final TOTStatsApiClient apiClient;
    private final LeaderboardChannelRepository leaderboardChannelRepository;
    private final MessageService messageService;

    public Optional<DiscordLeaderboardResponse> fetchLeaderboard(StatisticType category, int page) {
        return apiClient.getLeaderboard(category, page);
    }

    public MessageEmbed buildLeaderboardEmbed(String guildId, Guild guild, StatisticType category,
                                               DiscordLeaderboardResponse response, boolean includeFooter) {
        String categoryName = getCategoryDisplayName(guildId, category);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(messageService.getMessage(guildId, "leaderboard.title", categoryName))
                .setColor(0x5865F2);

        List<DiscordLeaderboardEntry> entries = response.getResults();
        if (entries == null || entries.isEmpty()) {
            embed.setDescription(messageService.getMessage(guildId, "leaderboard.empty"));
        } else {
            int maxRankWidth = entries.stream()
                    .mapToInt(e -> e.getRanking() != null ? String.valueOf(e.getRanking()).length() : 1)
                    .max().orElse(1);

            StringBuilder description = new StringBuilder();
            for (DiscordLeaderboardEntry entry : entries) {
                description.append(formatEntry(guildId, entry, guild, maxRankWidth));
            }
            embed.setDescription(description.toString());
        }

        if (includeFooter) {
            Integer currentPage = response.getCurrentPage();
            Integer totalPages = response.getTotalPages();
            Integer totalResults = response.getTotalResults();
            embed.setFooter(messageService.getMessage(guildId, "leaderboard.footer",
                    currentPage != null ? currentPage : 1,
                    totalPages != null ? totalPages : 1,
                    totalResults != null ? totalResults : 0));
        }

        return embed.build();
    }

    private String formatEntry(String guildId, DiscordLeaderboardEntry entry, Guild guild, int maxRankWidth) {
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
        String discordUserId = entry.getDiscordUserId() != null && entry.getDiscordUserId().isPresent()
                ? entry.getDiscordUserId().get() : null;
        if (guild != null && discordUserId != null && guild.getMemberById(discordUserId) != null) {
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
