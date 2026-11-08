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
import java.util.Collection;
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
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class LeaderboardService {

    /** Discord allows at most 100 user ids per member chunk request. */
    static final int MEMBER_CHUNK_SIZE = 100;
    private static final Duration MEMBER_RETRIEVE_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_NAME_CODE_POINTS = 20;
    private static final String TRUNCATION_FALLBACK = "\u2026";

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

        List<DiscordLeaderboardEntry> entries = response.getResults();
        if (entries == null || entries.isEmpty()) {
            embed.setDescription(messageService.getMessage(guildId, "leaderboard.empty"));
        } else {
            int maxRankWidth = entries.stream()
                    .mapToInt(e -> e.getRanking() != null ? String.valueOf(e.getRanking()).length() : 1)
                    .max().orElse(1);

            Set<String> guildMemberIds = resolveGuildMemberIds(guild, entries);

            List<String> lines = new ArrayList<>(entries.size());
            for (DiscordLeaderboardEntry entry : entries) {
                lines.add(formatEntry(guildId, entry, guildMemberIds, maxRankWidth));
            }
            // The title and footer are already set, so embed.length() is what they use of the total limit.
            int budget = Math.min(MessageEmbed.DESCRIPTION_MAX_LENGTH,
                    MessageEmbed.EMBED_MAX_LENGTH_BOT - embed.length());
            embed.setDescription(buildDescription(guildId, lines, Math.max(0, budget)));
        }

        return embed.build();
    }

    /**
     * Joins the entry lines without exceeding {@code budget} characters. Lines that do not fit are dropped
     * and replaced by a short indicator saying how many entries were left out.
     */
    private String buildDescription(String guildId, List<String> lines, int budget) {
        int totalLength = lines.stream().mapToInt(String::length).sum();
        if (totalLength <= budget) {
            return String.join("", lines);
        }

        // The indicator for all lines is at least as long as any shorter one, so reserving it is always enough.
        int reserve = truncationIndicator(guildId, lines.size()).length();
        StringBuilder description = new StringBuilder();
        int shown = 0;
        for (String line : lines) {
            if (description.length() + line.length() + reserve > budget) {
                break;
            }
            description.append(line);
            shown++;
        }
        description.append(truncationIndicator(guildId, lines.size() - shown));
        return truncateCodePoints(description.toString(), budget);
    }

    private String truncationIndicator(String guildId, int hiddenEntries) {
        String indicator = messageService.getMessage(guildId, "leaderboard.truncated", hiddenEntries);
        return indicator != null && !indicator.isBlank() ? indicator : TRUNCATION_FALLBACK;
    }

    /** Cuts {@code text} to at most {@code maxChars} UTF-16 chars without splitting a surrogate pair. */
    private static String truncateCodePoints(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        int end = maxChars;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
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
        if (rawName.codePointCount(0, rawName.length()) > MAX_NAME_CODE_POINTS) {
            int end = rawName.offsetByCodePoints(0, MAX_NAME_CODE_POINTS - 3);
            rawName = rawName.substring(0, end) + "...";
        }
        String safeName = escapeName(rawName);

        String displayName = entry.getProfileId() != null
                ? "[" + safeName + "](https://outlasttrialsstats.com/profile/" + entry.getProfileId() + ")"
                : safeName;

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

    /**
     * Escapes markdown in a player name, including single tokens and the brackets of masked links.
     * Backslashes are doubled first so a trailing one cannot escape the surrounding formatting.
     */
    static String escapeName(String name) {
        String escaped = MarkdownSanitizer.escape(name.replace("\\", "\\\\"), true);
        return escaped.replace("[", "\\[").replace("]", "\\]");
    }

    /**
     * Creates or replaces the binding for the guild and category.
     *
     * @return a snapshot of the binding as it was before, so the caller can delete its old messages
     */
    @Transactional
    public Optional<LeaderboardBinding> saveBinding(String guildId, StatisticType category, String channelId,
                                                    List<String> messageIds, int maxPages) {
        Optional<LeaderboardChannel> existing = leaderboardChannelRepository
                .findByGuildIdAndCategory(guildId, category);

        if (existing.isPresent()) {
            LeaderboardChannel binding = existing.get();
            LeaderboardBinding previous = LeaderboardBinding.of(binding);
            binding.setChannelId(channelId);
            binding.setMessageIds(new ArrayList<>(messageIds));
            binding.setMaxPages(maxPages);
            leaderboardChannelRepository.save(binding);
            return Optional.of(previous);
        }
        leaderboardChannelRepository.save(new LeaderboardChannel(guildId, category, channelId, messageIds, maxPages));
        return Optional.empty();
    }

    @Transactional
    public Optional<LeaderboardBinding> removeBinding(String guildId, StatisticType category) {
        return leaderboardChannelRepository.findByGuildIdAndCategory(guildId, category)
                .map(this::delete);
    }

    /**
     * Removes the binding only if it still references {@code messageId}. A stale failure for a message
     * that has since been replaced by a new setup must not delete the new binding.
     */
    @Transactional
    public Optional<LeaderboardBinding> removeBindingIfContainsMessage(Long bindingId, String messageId) {
        if (bindingId == null) {
            return Optional.empty();
        }
        return leaderboardChannelRepository.findById(bindingId)
                .filter(binding -> binding.getMessageIds().contains(messageId))
                .map(this::delete);
    }

    /** Removes the binding only if it still points to {@code channelId}. */
    @Transactional
    public Optional<LeaderboardBinding> removeBindingIfInChannel(Long bindingId, String channelId) {
        if (bindingId == null) {
            return Optional.empty();
        }
        return leaderboardChannelRepository.findById(bindingId)
                .filter(binding -> channelId.equals(binding.getChannelId()))
                .map(this::delete);
    }

    /** Removes every binding that references one of the given messages. */
    @Transactional
    public List<LeaderboardBinding> removeBindingsByMessageIds(Collection<String> messageIds) {
        if (messageIds.isEmpty()) {
            return List.of();
        }
        return leaderboardChannelRepository.findByMessageIdIn(messageIds).stream()
                .map(this::delete)
                .toList();
    }

    private LeaderboardBinding delete(LeaderboardChannel binding) {
        LeaderboardBinding snapshot = LeaderboardBinding.of(binding);
        leaderboardChannelRepository.delete(binding);
        return snapshot;
    }

    public List<LeaderboardBinding> getAllBindings() {
        return leaderboardChannelRepository.findAllWithMessageIds().stream()
                .map(LeaderboardBinding::of)
                .toList();
    }

    public String getCategoryDisplayName(String guildId, StatisticType category) {
        return messageService.getMessage(guildId, "leaderboard.category." + category.getValue());
    }
}
