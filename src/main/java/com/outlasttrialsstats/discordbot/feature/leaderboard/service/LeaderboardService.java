package com.outlasttrialsstats.discordbot.feature.leaderboard.service;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardEntry;
import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import com.outlasttrialsstats.discordbot.feature.leaderboard.LeaderboardCategories;
import com.outlasttrialsstats.discordbot.repository.LeaderboardChannelRepository;
import com.outlasttrialsstats.discordbot.shared.BotColors;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import com.outlasttrialsstats.discordbot.shared.SiteLinks;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
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
                .setColor(BotColors.BRAND);

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
                lines.add(formatEntry(guildId, category, entry, guildMemberIds, maxRankWidth));
            }
            int budget = Math.min(MessageEmbed.DESCRIPTION_MAX_LENGTH,
                    MessageEmbed.EMBED_MAX_LENGTH_BOT - embed.length());
            embed.setDescription(buildDescription(guildId, lines, Math.max(0, budget)));
        }

        return embed.build();
    }

    private String buildDescription(String guildId, List<String> lines, int budget) {
        int totalLength = lines.stream().mapToInt(String::length).sum();
        if (totalLength <= budget) {
            return String.join("", lines);
        }

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
        return truncateChars(description.toString(), budget);
    }

    private String truncationIndicator(String guildId, int hiddenEntries) {
        String indicator = messageService.getMessage(guildId, "leaderboard.truncated", hiddenEntries);
        return indicator != null && !indicator.isBlank() ? indicator : TRUNCATION_FALLBACK;
    }

    private static String truncateChars(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        int end = maxChars;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    private Set<String> resolveGuildMemberIds(Guild guild, List<DiscordLeaderboardEntry> entries) {
        if (guild == null) {
            return Set.of();
        }

        Set<Long> requestedIds = new LinkedHashSet<>();
        for (DiscordLeaderboardEntry entry : entries) {
            String discordUserId = getDiscordUserId(entry);
            if (discordUserId != null) {
                try {
                    requestedIds.add(Long.parseUnsignedLong(discordUserId));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (requestedIds.isEmpty()) {
            return Set.of();
        }

        List<Long> idList = new ArrayList<>(requestedIds);
        Set<String> memberIds = new HashSet<>();
        for (int start = 0; start < idList.size(); start += MEMBER_CHUNK_SIZE) {
            List<Long> batch = idList.subList(start, Math.min(start + MEMBER_CHUNK_SIZE, idList.size()));
            long[] ids = batch.stream().mapToLong(Long::longValue).toArray();
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
                for (long id : batch) {
                    String memberId = Long.toUnsignedString(id);
                    if (guild.getMemberById(memberId) != null) {
                        memberIds.add(memberId);
                    }
                }
            }
        }
        return memberIds;
    }

    private static String getDiscordUserId(DiscordLeaderboardEntry entry) {
        return entry.getDiscordUserId();
    }

    private String formatEntry(String guildId, StatisticType category, DiscordLeaderboardEntry entry,
                               Set<String> guildMemberIds, int maxRankWidth) {
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
                ? "[" + safeName + "](" + SiteLinks.SITE_URL + "/profile/" + entry.getProfileId() + ")"
                : safeName;

        String mention = "";
        String discordUserId = getDiscordUserId(entry);
        if (discordUserId != null && guildMemberIds.contains(discordUserId)) {
            mention = " (<@" + discordUserId + ">)";
        }

        int ranking = entry.getRanking() != null ? entry.getRanking() : 0;
        long value = entry.getValue() != null ? entry.getValue() : 0L;
        String rankPadding = " ".repeat(maxRankWidth - String.valueOf(ranking).length());
        String formattedValue = LeaderboardCategories.formatValue(category, value);

        return messageService.getMessage(guildId, "leaderboard.entry",
                rankPadding, String.valueOf(ranking), displayName, mention, formattedValue) + "\n";
    }

    static String escapeName(String name) {
        String escaped = MarkdownSanitizer.escape(name.replace("\\", "\\\\"), true);
        return escaped.replace("[", "\\[").replace("]", "\\]");
    }

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
    public Optional<LeaderboardBinding> removeBindingIfContainsMessage(Long bindingId, String messageId) {
        return removeBindingIf(bindingId, binding -> binding.getMessageIds().contains(messageId));
    }

    @Transactional
    public Optional<LeaderboardBinding> removeBindingIfInChannel(Long bindingId, String channelId) {
        return removeBindingIf(bindingId, binding -> channelId.equals(binding.getChannelId()));
    }

    private Optional<LeaderboardBinding> removeBindingIf(Long bindingId, Predicate<LeaderboardChannel> condition) {
        if (bindingId == null) {
            return Optional.empty();
        }
        return leaderboardChannelRepository.findById(bindingId)
                .filter(condition)
                .map(this::delete);
    }

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

    public List<LeaderboardBinding> getBindings(String guildId) {
        return leaderboardChannelRepository.findByGuildIdWithMessageIds(guildId).stream()
                .map(LeaderboardBinding::of)
                .toList();
    }

    public long countBindings(String guildId) {
        return leaderboardChannelRepository.countByGuildId(guildId);
    }

    @Transactional
    public Optional<LeaderboardBinding> removeBinding(String guildId, long bindingId) {
        return removeBindingIf(bindingId, binding -> guildId.equals(binding.getGuildId()));
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
