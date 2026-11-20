package com.outlasttrialsstats.discordbot.feature.leaderboard;

import com.outlasttrialsstats.backend.api.model.StatisticType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;

/**
 * Parses, suggests and formats leaderboard categories.
 */
public final class LeaderboardCategories {

    /** Discord accepts at most 25 autocomplete choices. */
    static final int MAX_CHOICES = 25;

    /** Old category names that may still be stored in button ids of posted messages. */
    private static final Map<String, StatisticType> RENAMED = Map.of(
            "ESCALATION_PEAK", StatisticType.ESCALATION_HIGHEST_TRIAL_REACHED);

    /** ESCALATION_HIGHEST_TRIAL_REACHED_WITH_TIME encodes trial * TRIAL_FACTOR + (TIME_OFFSET - seconds). */
    private static final long TRIAL_FACTOR = 4_194_304L;
    private static final long TIME_OFFSET = 1_048_576L;

    private LeaderboardCategories() {
    }

    public static Optional<StatisticType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        StatisticType renamed = RENAMED.get(normalized);
        if (renamed != null) {
            return Optional.of(renamed);
        }
        return Arrays.stream(StatisticType.values())
                .filter(type -> type.getValue().equals(normalized))
                .findFirst();
    }

    /**
     * Returns the categories whose display name or value contains {@code typed}, at most {@link #MAX_CHOICES}.
     */
    public static List<Choice> suggest(String typed, Function<StatisticType, String> displayName) {
        String needle = typed == null ? "" : typed.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(StatisticType.values())
                .map(type -> new Choice(displayName.apply(type), type.getValue()))
                .filter(choice -> choice.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || choice.getAsString().toLowerCase(Locale.ROOT).contains(needle))
                .limit(MAX_CHOICES)
                .toList();
    }

    public static String formatValue(StatisticType category, long value) {
        return switch (category) {
            case ESCALATION_TIME_PLAYED -> formatSeconds(value);
            case ESCALATION_HIGHEST_TRIAL_REACHED_WITH_TIME -> formatEscalationRun(value);
            default -> String.format(Locale.ROOT, "%,d", value);
        };
    }

    private static String formatEscalationRun(long value) {
        if (value <= 0) {
            return "-";
        }
        long trial = value / TRIAL_FACTOR;
        long seconds = TIME_OFFSET - (value % TRIAL_FACTOR);
        return trial + " · " + formatSeconds(seconds);
    }

    private static String formatSeconds(long seconds) {
        if (seconds <= 0) {
            return "-";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%,dh %dm", hours, minutes);
        }
        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }
}
