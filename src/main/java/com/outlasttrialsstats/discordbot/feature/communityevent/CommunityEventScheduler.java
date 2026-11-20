package com.outlasttrialsstats.discordbot.feature.communityevent;

import com.outlasttrialsstats.backend.api.model.CommunityEventType;
import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import com.outlasttrialsstats.discordbot.entity.CommunityAlertType;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.AlertRule;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.CommunityAlertEvaluator;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.CommunityAlertEvaluator.AlertProgress;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.CommunityAlertEvaluator.CommunityAlert;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.CommunityAlertEvaluator.Evaluation;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventBinding;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventService;
import com.outlasttrialsstats.discordbot.shared.BotChannels;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CommunityEventScheduler {

    static final Duration MIN_EDIT_INTERVAL = Duration.ofMinutes(5);
    static final Duration ALERT_GRACE_AFTER_END = Duration.ofHours(1);

    private final JDA jda;
    private final CommunityEventService service;
    private final CommunityEventEmbedFactory embedFactory;
    private final Clock clock;
    private final Map<String, Rendered> rendered = new ConcurrentHashMap<>();

    @Autowired
    // @Lazy breaks the cycle jda -> commandRouter -> communityEventSetupCommand -> scheduler -> jda
    public CommunityEventScheduler(@Lazy JDA jda, CommunityEventService service, CommunityEventEmbedFactory embedFactory) {
        this(jda, service, embedFactory, Clock.systemUTC());
    }

    CommunityEventScheduler(JDA jda, CommunityEventService service, CommunityEventEmbedFactory embedFactory,
                            Clock clock) {
        this.jda = jda;
        this.service = service;
        this.embedFactory = embedFactory;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60, initialDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void poll() {
        Optional<List<DiscordCommunityEvent>> events = service.refreshEvents();
        if (events.isEmpty()) {
            log.debug("No community event data, skipping update");
            return;
        }
        Instant now = clock.instant();
        for (CommunityEventBinding binding : service.getAllBindings()) {
            try {
                updateMessage(binding, events.get(), now);
            } catch (Exception e) {
                log.warn("Failed to update community event message in guild {}: {}", binding.guildId(),
                        e.getMessage());
            }
        }
        processAllAlerts(events.get(), now);
    }

    void updateMessage(CommunityEventBinding binding, List<DiscordCommunityEvent> events, Instant now) {
        StandardGuildMessageChannel channel = jda.getChannelById(StandardGuildMessageChannel.class,
                binding.channelId());
        if (channel == null) {
            if (!jda.getUnavailableGuilds().contains(binding.guildId())) {
                log.info("Channel {} no longer exists, removing community event message of guild {}",
                        binding.channelId(), binding.guildId());
                service.removeBindingIfMessage(binding.guildId(), binding.messageId());
                rendered.remove(binding.guildId());
            }
            return;
        }
        if (channel.isObfuscated() || !channel.getGuild().getSelfMember().hasAccess(channel)) {
            log.warn("No access to channel {}, skipping community event message", binding.channelId());
            return;
        }

        Optional<DiscordCommunityEvent> target = binding.followsCurrentEvent()
                ? CommunityEvents.selectCurrent(events, now)
                : CommunityEvents.find(events, binding.eventId());
        if (target.isEmpty()) {
            log.debug("No community event to show for guild {}", binding.guildId());
            return;
        }
        DiscordCommunityEvent event = target.get();
        if (!event.getId().equals(binding.shownEventId())) {
            switchEvent(channel, binding, event, events, now);
            return;
        }

        String significant = significantState(event, now);
        Rendered last = rendered.get(binding.guildId());
        boolean sameMessage = last != null && last.messageId().equals(binding.messageId());
        if (sameMessage && last.significant().equals(significant) && last.at().plus(MIN_EDIT_INTERVAL).isAfter(now)) {
            return;
        }
        MessageEmbed embed = embedFactory.buildProgressEmbed(binding.guildId(), event, now);
        Rendered next = new Rendered(binding.messageId(), significant, embed.toData().toString(), now);
        if (sameMessage && last.content().equals(next.content())) {
            return;
        }

        channel.editMessageEmbedsById(binding.messageId(), embed)
                .setComponents(embedFactory.buildButtons(binding.guildId(), event))
                .queue(_ -> rendered.put(binding.guildId(), next),
                        error -> handleEditFailure(binding, error));
    }

    private void switchEvent(StandardGuildMessageChannel channel, CommunityEventBinding binding,
                             DiscordCommunityEvent event, List<DiscordCommunityEvent> events, Instant now) {
        CommunityEvents.find(events, binding.shownEventId()).ifPresent(previous -> channel
                .editMessageEmbedsById(binding.messageId(),
                        embedFactory.buildProgressEmbed(binding.guildId(), previous, now))
                .queue(_ -> {}, error -> log.debug("Could not finalize community event message {}: {}",
                        binding.messageId(), error.getMessage())));

        channel.sendMessageEmbeds(embedFactory.buildProgressEmbed(binding.guildId(), event, now))
                .setComponents(embedFactory.buildButtons(binding.guildId(), event))
                .queue(message -> adoptSwitchedMessage(binding, event, message),
                        error -> log.warn("Failed to post community event message for {} in guild {}: {}",
                                event.getId(), binding.guildId(), error.getMessage()));
    }

    private void adoptSwitchedMessage(CommunityEventBinding binding, DiscordCommunityEvent event, Message message) {
        boolean adopted = false;
        try {
            adopted = service.switchShownEvent(binding.guildId(), binding.messageId(), event.getId(),
                    message.getId());
        } catch (Exception e) {
            log.warn("Failed to save community event message of guild {}: {}", binding.guildId(), e.getMessage());
        }
        if (!adopted) {
            message.delete().queue(_ -> {}, _ -> {});
            return;
        }
        rendered.remove(binding.guildId());
        log.info("Community event message of guild {} switched from {} to {}", binding.guildId(),
                binding.shownEventId(), event.getId());
    }

    private void handleEditFailure(CommunityEventBinding binding, Throwable error) {
        if (error instanceof ErrorResponseException ere && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
            log.info("Community event message {} no longer exists, removing it for guild {}",
                    binding.messageId(), binding.guildId());
            service.removeBindingIfMessage(binding.guildId(), binding.messageId());
            rendered.remove(binding.guildId());
            return;
        }
        log.warn("Failed to update community event message {}: {}", binding.messageId(), error.getMessage());
    }

    private static String significantState(DiscordCommunityEvent event, Instant now) {
        return event.getId() + ":" + CommunityEvents.status(event, now) + ":"
                + CommunityEvents.highestReachedIndex(event);
    }

    public void forget(String guildId) {
        rendered.remove(guildId);
    }

    private void processAllAlerts(List<DiscordCommunityEvent> events, Instant now) {
        List<DiscordCommunityEvent> relevant = events.stream().filter(event -> isAlertRelevant(event, now)).toList();
        if (relevant.isEmpty()) {
            return;
        }
        Map<String, String> channels = service.getAlertChannelsByGuild();
        if (channels.isEmpty()) {
            return;
        }
        Map<String, Map<CommunityAlertType, AlertRule>> rules = service.getRulesByGuild();
        Map<String, Map<String, AlertProgress>> progress = service.getProgressByGuild();
        channels.forEach((guildId, channelId) -> {
            try {
                processAlerts(guildId, channelId, relevant, rules.getOrDefault(guildId, Map.of()),
                        progress.getOrDefault(guildId, Map.of()), now);
            } catch (Exception e) {
                log.warn("Failed to process community event alerts in guild {}: {}", guildId, e.getMessage());
            }
        });
    }

    static boolean isAlertRelevant(DiscordCommunityEvent event, Instant now) {
        if (event.getType() != CommunityEventType.EVENT || event.getCurrentValue() == null) {
            return false;
        }
        if (Boolean.TRUE.equals(event.getActive())) {
            return true;
        }
        Instant endsAt = CommunityEvents.instant(event.getEndsAt());
        return endsAt != null && !endsAt.isAfter(now) && endsAt.plus(ALERT_GRACE_AFTER_END).isAfter(now);
    }

    void processAlerts(String guildId, String channelId, List<DiscordCommunityEvent> events,
                       Map<CommunityAlertType, AlertRule> rules, Map<String, AlertProgress> progress, Instant now) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            if (!jda.getUnavailableGuilds().contains(guildId)) {
                log.info("Bot is no longer in guild {}, turning off its community event alerts", guildId);
                service.removeAlertChannelIf(guildId, channelId);
            }
            return;
        }
        StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class, channelId);
        if (channel == null) {
            log.info("Alert channel {} no longer exists, turning off community event alerts of guild {}",
                    channelId, guildId);
            service.removeAlertChannelIf(guildId, channelId);
            return;
        }

        Map<String, AlertProgress> changed = new HashMap<>();
        List<PendingAlert> pending = new ArrayList<>();
        for (DiscordCommunityEvent event : events) {
            Evaluation evaluation = CommunityAlertEvaluator.evaluate(event, progress.get(event.getId()), rules, now);
            if (evaluation.changed()) {
                changed.put(event.getId(), evaluation.progress());
            }
            evaluation.alerts().forEach(alert -> pending.add(new PendingAlert(event, alert)));
        }

        service.saveProgress(guildId, changed);
        if (pending.isEmpty()) {
            return;
        }
        if (!guild.getSelfMember().hasPermission(channel, BotChannels.SEND_EMBEDS)) {
            log.warn("Missing permissions in alert channel {} of guild {}, dropping {} alerts", channelId, guildId,
                    pending.size());
            return;
        }
        for (PendingAlert alert : pending) {
            sendAlert(guild, channel, alert, rules.getOrDefault(alert.alert().type(),
                    AlertRule.defaults(alert.alert().type())));
        }
    }

    private void sendAlert(Guild guild, StandardGuildMessageChannel channel, PendingAlert pending, AlertRule rule) {
        MessageCreateBuilder message = new MessageCreateBuilder()
                .setEmbeds(embedFactory.buildAlertEmbed(guild.getId(), pending.event(), pending.alert()))
                .setComponents(embedFactory.buildButtons(guild.getId(), pending.event()));
        applyPing(message, guild, rule);
        channel.sendMessage(message.build()).queue(
                _ -> log.info("Sent {} alert for phase {} of {} in guild {}", pending.alert().type(),
                        pending.alert().milestoneIndex() + 1, pending.event().getId(), guild.getId()),
                error -> log.warn("Failed to send community event alert in guild {}: {}", guild.getId(),
                        error.getMessage()));
    }

    static void applyPing(MessageCreateBuilder message, Guild guild, AlertRule rule) {
        message.setAllowedMentions(List.of());
        if (rule.roleId() == null) {
            return;
        }
        Role role = guild.getRoleById(rule.roleId());
        if (role == null) {
            return;
        }
        if (role.isPublicRole()) {
            message.setContent("@everyone").setAllowedMentions(List.of(Message.MentionType.EVERYONE));
        } else {
            message.setContent(role.getAsMention()).mentionRoles(role.getId());
        }
    }

    private record PendingAlert(DiscordCommunityEvent event, CommunityAlert alert) {
    }

    private record Rendered(String messageId, String significant, String content, Instant at) {
    }
}
