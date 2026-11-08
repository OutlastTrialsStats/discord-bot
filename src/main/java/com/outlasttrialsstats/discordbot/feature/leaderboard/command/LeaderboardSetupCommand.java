package com.outlasttrialsstats.discordbot.feature.leaderboard.command;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardBinding;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LeaderboardSetupCommand {

    /** Sending and later editing the embeds needs all of these in the target channel. */
    static final Permission[] REQUIRED_PERMISSIONS = {
            Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS,
            Permission.MESSAGE_HISTORY
    };

    private final LeaderboardService leaderboardService;
    private final MessageService messageService;

    public void onSetupLeaderboard(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);

        event.deferReply(true).queue();
        var hook = event.getHook();

        var channelUnion = event.getOption("channel").getAsChannel();
        if (channelUnion.getType() != ChannelType.TEXT && channelUnion.getType() != ChannelType.NEWS) {
            hook.editOriginal(messageService.getMessage(guildId, "error.invalid_channel_type")).queue();
            return;
        }
        StandardGuildMessageChannel channel = channelUnion.asStandardGuildMessageChannel();

        if (!guild.getSelfMember().hasPermission(channel, REQUIRED_PERMISSIONS)) {
            hook.editOriginal(messageService.getMessage(guildId, "error.missing_permission.channel_write", channel.getAsMention())).queue();
            return;
        }

        String categoryValue = event.getOption("category").getAsString();
        StatisticType statisticType = StatisticType.fromValue(categoryValue);
        String categoryName = leaderboardService.getCategoryDisplayName(guildId, statisticType);
        int maxPages = Math.max(1, Math.min(10, event.getOption("pages").getAsInt()));

        // Verify first page is available
        Optional<DiscordLeaderboardResponse> firstResponse = leaderboardService.fetchLeaderboard(statisticType, 1);
        if (firstResponse.isEmpty()) {
            hook.editOriginal(messageService.getMessage(guildId, "leaderboard.error")).queue();
            return;
        }

        // Cap maxPages to actual total pages, but always post at least one (possibly empty) page
        Integer reportedPages = firstResponse.get().getTotalPages();
        int totalPages = Math.max(1, reportedPages != null ? reportedPages : 1);
        maxPages = Math.min(maxPages, totalPages);

        // Post the new messages first, so a failure leaves the existing leaderboard untouched
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
            hook.editOriginal(messageService.getMessage(guildId, "setup.leaderboard.post_failed",
                    channel.getAsMention())).queue();
            return;
        }

        oldBinding.ifPresent(binding -> deleteMessages(guild, binding.channelId(), binding.messageIds()));

        hook.editOriginal(messageService.getMessage(guildId, "setup.leaderboard.success",
                        categoryName, channel.getAsMention()))
                .queue();
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
            for (String messageId : messageIds) {
                targetChannel.deleteMessageById(messageId).queue(
                        _ -> {},
                        _ -> log.debug("Could not delete leaderboard message {}", messageId)
                );
            }
        } catch (Exception e) {
            log.debug("Could not delete leaderboard messages: {}", e.getMessage());
        }
    }
}
