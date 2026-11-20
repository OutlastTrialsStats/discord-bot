package com.outlasttrialsstats.discordbot.feature.leaderboard.service;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.shared.BotChannels;
import com.outlasttrialsstats.discordbot.shared.MessageCleanup;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardSetupService {

    public static final int MAX_PAGES = 10;
    static final Permission[] REQUIRED_PERMISSIONS = BotChannels.SEND_AND_EDIT_EMBEDS;

    private final LeaderboardService leaderboardService;
    private final MessageService messageService;

    public String create(Guild guild, StandardGuildMessageChannel channel, StatisticType statisticType, int pages) {
        String guildId = guild.getId();
        if (!guild.getSelfMember().hasPermission(channel, REQUIRED_PERMISSIONS)) {
            return messageService.getMessage(guildId, "error.missing_permission.channel_write", channel.getAsMention());
        }
        String categoryName = leaderboardService.getCategoryDisplayName(guildId, statisticType);
        int maxPages = Math.max(1, Math.min(MAX_PAGES, pages));

        Optional<DiscordLeaderboardResponse> firstResponse = leaderboardService.fetchLeaderboard(statisticType, 1);
        if (firstResponse.isEmpty()) {
            return messageService.getMessage(guildId, "leaderboard.error");
        }

        Integer reportedPages = firstResponse.get().getTotalPages();
        int totalPages = Math.max(1, reportedPages != null ? reportedPages : 1);
        maxPages = Math.min(maxPages, totalPages);

        List<String> messageIds = new ArrayList<>();
        Optional<LeaderboardBinding> oldBinding;
        try {
            for (int page = 1; page <= maxPages; page++) {
                Optional<DiscordLeaderboardResponse> response = page == 1
                        ? firstResponse
                        : leaderboardService.fetchLeaderboard(statisticType, page);
                if (response.isEmpty()) break;

                MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(
                        guildId, guild, statisticType, response.get(), page == 1, true, false);
                var message = channel.sendMessageEmbeds(embed).complete();
                messageIds.add(message.getId());
            }
            oldBinding = leaderboardService.saveBinding(guildId, statisticType, channel.getId(),
                    messageIds, messageIds.size());
        } catch (Exception e) {
            log.warn("Failed to set up leaderboard {} in guild {}: {}", statisticType, guildId, e.getMessage());
            deleteMessages(guild, channel.getId(), messageIds);
            return messageService.getMessage(guildId, "setup.leaderboard.post_failed", channel.getAsMention());
        }

        oldBinding.ifPresent(binding -> deleteMessages(guild, binding.channelId(), binding.messageIds()));

        return messageService.getMessage(guildId, "setup.leaderboard.success", categoryName, channel.getAsMention());
    }

    public List<LeaderboardBinding> list(String guildId) {
        return leaderboardService.getBindings(guildId);
    }

    public long count(String guildId) {
        return leaderboardService.countBindings(guildId);
    }

    public String remove(Guild guild, long bindingId) {
        String guildId = guild.getId();
        Optional<LeaderboardBinding> removed = leaderboardService.removeBinding(guildId, bindingId);
        if (removed.isEmpty()) {
            return messageService.getMessage(guildId, "setup.leaderboard.remove.nothing");
        }
        LeaderboardBinding binding = removed.get();
        deleteMessages(guild, binding.channelId(), binding.messageIds());
        return messageService.getMessage(guildId, "setup.leaderboard.remove.success",
                leaderboardService.getCategoryDisplayName(guildId, binding.category()));
    }

    private void deleteMessages(Guild guild, String channelId, List<String> messageIds) {
        if (messageIds.isEmpty()) {
            return;
        }
        try {
            StandardGuildMessageChannel targetChannel = guild.getChannelById(StandardGuildMessageChannel.class, channelId);
            if (targetChannel == null) {
                return;
            }
            MessageCleanup.deleteMessages(targetChannel, messageIds, Set.of());
        } catch (Exception e) {
            log.debug("Could not delete leaderboard messages: {}", e.getMessage());
        }
    }
}
