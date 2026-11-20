package com.outlasttrialsstats.discordbot.feature.communityevent;

import com.outlasttrialsstats.backend.api.model.CommunityEventArt;
import com.outlasttrialsstats.backend.api.model.CommunityEventMilestone;
import com.outlasttrialsstats.backend.api.model.CommunityEventStep;
import com.outlasttrialsstats.backend.api.model.CommunityEventType;
import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import com.outlasttrialsstats.discordbot.entity.CommunityAlertType;
import com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEvents.Status;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.CommunityAlertEvaluator.CommunityAlert;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommunityEventEmbedFactory {

    public static final int COLOR = 0xB3261E;
    static final int BAR_LENGTH = 12;
    static final int FORECAST_PHASES = 2;
    private static final String ZERO_WIDTH_SPACE = Character.toString(0x200B);

    private final MessageService messageService;

    public MessageEmbed buildProgressEmbed(String guildId, DiscordCommunityEvent event, Instant now) {
        Render render = new Render(guildId);
        CommunityEventArt art = CommunityEvents.art(event);
        EmbedBuilder embed = baseEmbed(event)
                .setThumbnail(CommunityEvents.assetUrl(art.getIcon()));

        List<String> lines = new ArrayList<>();
        lines.add(statusLine(render, event, now));
        if (event.getCurrentValue() != null) {
            lines.add(render.message("community_event.total", render.number(event.getCurrentValue())));
        }
        lines.add("");
        if (event.getType() == CommunityEventType.COUNTER) {
            addCounter(lines, render, event);
        } else {
            addPhases(lines, render, event);
        }
        if (event.getRatePerHour() != null && event.getRatePerHour() > 0) {
            embed.addField(render.message("community_event.field.rate"),
                    render.message("community_event.rate", render.number(event.getRatePerHour())), true);
        }
        if (event.getSampledAt() != null) {
            embed.addField(render.message("community_event.field.updated"),
                    TimeFormat.TIME_SHORT.format(event.getSampledAt()), true);
        }
        if (!embed.getFields().isEmpty()) {
            lines.add(ZERO_WIDTH_SPACE);
        }
        embed.setDescription(String.join("\n", lines));
        return embed.build();
    }

    public MessageEmbed buildAlertEmbed(String guildId, DiscordCommunityEvent event, CommunityAlert alert) {
        Render render = new Render(guildId);
        CommunityEventMilestone milestone = CommunityEvents.milestones(event).get(alert.milestoneIndex());
        String phase = String.valueOf(alert.milestoneIndex() + 1);
        String amount = render.number(milestone.getAmount());
        EmbedBuilder embed = baseEmbed(event)
                .setThumbnail(CommunityEvents.assetUrl(milestone.getImage()));

        if (alert.type() == CommunityAlertType.MILESTONE_REACHED) {
            embed.setTitle(render.message("community_event.alert.milestone.title", phase),
                            CommunityEvents.eventUrl(event))
                    .setDescription(render.message("community_event.alert.milestone.description",
                            event.getName(), amount));
        } else {
            String remaining = render.number(Math.max(0, milestone.getAmount() - CommunityEvents.currentValue(event)));
            String eta = event.getEtaNextMilestone() == null
                    ? "" : TimeFormat.RELATIVE.format(event.getEtaNextMilestone());
            embed.setTitle(render.message("community_event.alert.pre.title", phase), CommunityEvents.eventUrl(event))
                    .setDescription(render.message("community_event.alert.pre.description",
                            event.getName(), remaining, amount, eta));
        }
        return embed.build();
    }

    private EmbedBuilder baseEmbed(DiscordCommunityEvent event) {
        return new EmbedBuilder()
                .setColor(COLOR)
                .setTitle(event.getName(), CommunityEvents.eventUrl(event));
    }

    private String statusLine(Render render, DiscordCommunityEvent event, Instant now) {
        Status status = CommunityEvents.status(event, now);
        if (status == Status.LIVE) {
            return event.getEndsAt() == null
                    ? render.message("community_event.status.live")
                    : render.message("community_event.status.live_until",
                    TimeFormat.RELATIVE.format(event.getEndsAt()));
        }
        if (status == Status.UPCOMING) {
            return render.message("community_event.status.upcoming", TimeFormat.RELATIVE.format(event.getStartsAt()));
        }
        String endedAt = event.getEndsAt() == null ? "" : TimeFormat.DATE_SHORT.format(event.getEndsAt());
        return render.message("community_event.status.ended", endedAt,
                String.valueOf(CommunityEvents.highestReachedIndex(event) + 1),
                String.valueOf(CommunityEvents.milestones(event).size()));
    }

    private void addPhases(List<String> lines, Render render, DiscordCommunityEvent event) {
        List<CommunityEventMilestone> milestones = CommunityEvents.milestones(event);
        int reached = CommunityEvents.highestReachedIndex(event);
        int next = reached + 1;

        if (next < milestones.size()) {
            long from = reached >= 0 ? milestones.get(reached).getAmount() : 0;
            long to = milestones.get(next).getAmount();
            lines.add(render.message("community_event.next", String.valueOf(next + 1), render.number(to)));
            lines.add(progressLine(render, CommunityEvents.currentValue(event), from, to));
        } else if (!milestones.isEmpty()) {
            lines.add(render.message("community_event.all_reached"));
        }
        lines.add("");

        Instant eta = CommunityEvents.instant(event.getEtaNextMilestone());
        Instant endsAt = CommunityEvents.instant(event.getEndsAt());
        boolean live = Boolean.TRUE.equals(event.getActive());
        for (CommunityEventMilestone milestone : milestones) {
            int index = milestone.getIndex();
            String phase = String.valueOf(index + 1);
            String amount = render.number(milestone.getAmount());
            if (index <= reached) {
                lines.add(milestone.getReachedAt() == null
                        ? render.message("community_event.phase.reached_unknown", phase, amount)
                        : render.message("community_event.phase.reached", phase, amount,
                        TimeFormat.RELATIVE.format(milestone.getReachedAt())));
            } else if (index == next) {
                lines.add(live && eta != null
                        ? render.message("community_event.phase.current", phase, amount, TimeFormat.RELATIVE.format(eta))
                        : render.message("community_event.phase.current_no_eta", phase, amount));
            } else {
                Instant forecast = live && index - next <= FORECAST_PHASES
                        ? forecast(eta, milestones.get(next).getAmount(), milestone.getAmount(), event.getRatePerHour())
                        : null;
                lines.add(forecast != null && (endsAt == null || forecast.isBefore(endsAt))
                        ? render.message("community_event.phase.forecast", phase, amount,
                        TimeFormat.RELATIVE.format(forecast))
                        : render.message("community_event.phase.locked", phase, amount));
            }
        }
    }

    private void addCounter(List<String> lines, Render render, DiscordCommunityEvent event) {
        if (event.getNextMilestone() != null) {
            long to = event.getNextMilestone();
            long from = event.getStep() == null ? 0 : Math.max(0, to - event.getStep());
            lines.add(render.message("community_event.counter.next", render.number(to)));
            lines.add(progressLine(render, CommunityEvents.currentValue(event), from, to));
        }
        List<CommunityEventStep> steps = event.getReachedSteps() == null ? List.of() : event.getReachedSteps();
        if (!steps.isEmpty()) {
            lines.add("");
        }
        for (CommunityEventStep step : steps) {
            lines.add(step.getReachedAt() == null
                    ? render.message("community_event.counter.step_unknown", render.number(step.getAmount()))
                    : render.message("community_event.counter.step", render.number(step.getAmount()),
                    TimeFormat.RELATIVE.format(step.getReachedAt())));
        }
    }

    private String progressLine(Render render, double current, long from, long to) {
        double span = Math.max(1, to - from);
        double ratio = Math.clamp((current - from) / span, 0, 1);
        return render.message("community_event.progress",
                progressBar(ratio),
                String.format(render.locale, "%.1f", ratio * 100),
                render.number(Math.max(0, to - current)));
    }

    static String progressBar(double ratio) {
        int filled = (int) Math.floor(Math.clamp(ratio, 0, 1) * BAR_LENGTH);
        return "▰".repeat(filled) + "▱".repeat(BAR_LENGTH - filled);
    }

    static Instant forecast(Instant nextEta, long nextAmount, long amount, Double ratePerHour) {
        if (nextEta == null || ratePerHour == null || ratePerHour <= 0) {
            return null;
        }
        double hours = (amount - nextAmount) / ratePerHour;
        return nextEta.plus(Duration.ofSeconds(Math.round(hours * 3600)));
    }

    private final class Render {

        private final String guildId;
        private final Locale locale;
        private final NumberFormat numberFormat;

        Render(String guildId) {
            this.guildId = guildId;
            this.locale = messageService.getLocale(guildId);
            this.numberFormat = NumberFormat.getIntegerInstance(locale);
        }

        String message(String key, Object... args) {
            return messageService.getMessage(guildId, key, args);
        }

        String number(double value) {
            return numberFormat.format(Math.floor(value));
        }

        String number(Long value) {
            return value == null ? "?" : numberFormat.format(value);
        }
    }
}
