package com.outlasttrialsstats.discordbot.feature.leaderboard.service;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardEntry;
import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import com.outlasttrialsstats.discordbot.repository.LeaderboardChannelRepository;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
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

    public MessageEmbed buildLeaderboardEmbed(String guildId, StatisticType category,
                                               DiscordLeaderboardResponse response, boolean includeTimestamp) {
        String categoryName = getCategoryDisplayName(guildId, category);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(messageService.getMessage(guildId, "leaderboard.title", categoryName))
                .setColor(0x5865F2);

        List<DiscordLeaderboardEntry> entries = response.getResults();
        if (entries == null || entries.isEmpty()) {
            embed.setDescription(messageService.getMessage(guildId, "leaderboard.empty"));
        } else {
            StringBuilder description = new StringBuilder();
            for (DiscordLeaderboardEntry entry : entries) {
                String displayName = entry.getDisplayName() != null ? entry.getDisplayName() : "Unknown";
                Integer value = entry.getValue();
                Integer ranking = entry.getRanking();

                String mention = "";
                if (entry.getDiscordUserId() != null && entry.getDiscordUserId().isPresent()) {
                    mention = " (<@" + entry.getDiscordUserId().get() + ">)";
                }

                description.append(String.format("#%d **%s**%s - %,d%n",
                        ranking != null ? ranking : 0,
                        displayName,
                        mention,
                        value != null ? value : 0));
            }
            embed.setDescription(description.toString());
        }

        Integer currentPage = response.getCurrentPage();
        Integer totalPages = response.getTotalPages();
        Integer totalResults = response.getTotalResults();
        embed.setFooter(messageService.getMessage(guildId, "leaderboard.footer",
                currentPage != null ? currentPage : 1,
                totalPages != null ? totalPages : 1,
                totalResults != null ? totalResults : 0));

        if (includeTimestamp) {
            embed.setTimestamp(Instant.now());
        }

        return embed.build();
    }

    @Transactional
    public void saveBinding(String guildId, StatisticType category, String channelId, String messageId) {
        Optional<LeaderboardChannel> existing = leaderboardChannelRepository
                .findByGuildIdAndCategory(guildId, category);

        if (existing.isPresent()) {
            LeaderboardChannel binding = existing.get();
            binding.setChannelId(channelId);
            binding.setMessageId(messageId);
            leaderboardChannelRepository.save(binding);
        } else {
            leaderboardChannelRepository.save(new LeaderboardChannel(guildId, category, channelId, messageId));
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
        return leaderboardChannelRepository.findAll();
    }

    public String getCategoryDisplayName(String guildId, StatisticType category) {
        return messageService.getMessage(guildId, "leaderboard.category." + category.getValue());
    }
}
