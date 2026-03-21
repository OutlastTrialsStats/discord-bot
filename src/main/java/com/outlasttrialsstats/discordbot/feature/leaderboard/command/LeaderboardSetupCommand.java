package com.outlasttrialsstats.discordbot.feature.leaderboard.command;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LeaderboardSetupCommand {

    private final LeaderboardService leaderboardService;
    private final MessageService messageService;

    public void onSetupLeaderboard(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        TextChannel channel = event.getOption("channel").getAsChannel().asTextChannel();
        String categoryValue = event.getOption("category").getAsString();
        StatisticType statisticType = StatisticType.fromValue(categoryValue);
        String categoryName = leaderboardService.getCategoryDisplayName(guildId, statisticType);
        int maxPages = Math.max(1, Math.min(10, event.getOption("pages").getAsInt()));

        // Verify first page is available
        Optional<DiscordLeaderboardResponse> firstResponse = leaderboardService.fetchLeaderboard(statisticType, 1);
        if (firstResponse.isEmpty()) {
            event.reply(messageService.getMessage(guildId, "leaderboard.error"))
                    .setEphemeral(true).queue();
            return;
        }

        // Cap maxPages to actual total pages
        int totalPages = firstResponse.get().getTotalPages() != null ? firstResponse.get().getTotalPages() : 1;
        maxPages = Math.min(maxPages, totalPages);

        // Delete old messages if binding exists
        Optional<LeaderboardChannel> oldBinding = leaderboardService.removeBinding(guildId, statisticType);
        oldBinding.ifPresent(binding -> deleteOldMessages(guild, binding));

        // Post embeds for each page
        List<String> messageIds = new ArrayList<>();
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

        leaderboardService.saveBinding(guildId, statisticType, channel.getId(), messageIds, maxPages);
        event.reply(messageService.getMessage(guildId, "setup.leaderboard.success",
                        categoryName, channel.getAsMention()))
                .setEphemeral(true).queue();
    }

    private void deleteOldMessages(Guild guild, LeaderboardChannel binding) {
        try {
            TextChannel oldChannel = guild.getTextChannelById(binding.getChannelId());
            if (oldChannel != null) {
                for (String messageId : binding.getMessageIds()) {
                    oldChannel.deleteMessageById(messageId).queue(
                            _ -> {},
                            _ -> log.debug("Could not delete old leaderboard message {}", messageId)
                    );
                }
            }
        } catch (Exception e) {
            log.debug("Could not delete old leaderboard messages: {}", e.getMessage());
        }
    }
}
