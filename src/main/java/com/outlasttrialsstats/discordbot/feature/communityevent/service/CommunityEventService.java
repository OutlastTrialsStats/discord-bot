package com.outlasttrialsstats.discordbot.feature.communityevent.service;

import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import com.outlasttrialsstats.discordbot.entity.CommunityAlertType;
import com.outlasttrialsstats.discordbot.entity.CommunityEventAlertConfig;
import com.outlasttrialsstats.discordbot.entity.CommunityEventAlertRule;
import com.outlasttrialsstats.discordbot.entity.CommunityEventAlertState;
import com.outlasttrialsstats.discordbot.entity.CommunityEventMessage;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.AlertRule;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.CommunityAlertEvaluator.AlertProgress;
import com.outlasttrialsstats.discordbot.repository.CommunityEventAlertConfigRepository;
import com.outlasttrialsstats.discordbot.repository.CommunityEventAlertRuleRepository;
import com.outlasttrialsstats.discordbot.repository.CommunityEventAlertStateRepository;
import com.outlasttrialsstats.discordbot.repository.CommunityEventMessageRepository;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CommunityEventService {

    static final Duration EVENTS_MAX_AGE = Duration.ofSeconds(60);

    private final TOTStatsApiClient apiClient;
    private final CommunityEventMessageRepository messageRepository;
    private final CommunityEventAlertConfigRepository alertConfigRepository;
    private final CommunityEventAlertRuleRepository alertRuleRepository;
    private final CommunityEventAlertStateRepository alertStateRepository;
    private final Clock clock;

    private volatile CachedEvents cachedEvents;

    @Autowired
    public CommunityEventService(TOTStatsApiClient apiClient, CommunityEventMessageRepository messageRepository,
                                 CommunityEventAlertConfigRepository alertConfigRepository,
                                 CommunityEventAlertRuleRepository alertRuleRepository,
                                 CommunityEventAlertStateRepository alertStateRepository) {
        this(apiClient, messageRepository, alertConfigRepository, alertRuleRepository, alertStateRepository,
                Clock.systemUTC());
    }

    public Optional<List<DiscordCommunityEvent>> refreshEvents() {
        Optional<List<DiscordCommunityEvent>> events = apiClient.getCommunityEvents();
        events.ifPresent(list -> cachedEvents = new CachedEvents(List.copyOf(list), clock.instant()));
        return events;
    }

    public Optional<List<DiscordCommunityEvent>> getEvents() {
        CachedEvents cached = cachedEvents;
        if (cached != null && cached.fetchedAt().plus(EVENTS_MAX_AGE).isAfter(clock.instant())) {
            return Optional.of(cached.events());
        }
        return refreshEvents();
    }

    public List<DiscordCommunityEvent> getCachedEvents() {
        CachedEvents cached = cachedEvents;
        return cached == null ? List.of() : cached.events();
    }

    public Optional<CommunityEventBinding> getBinding(String guildId) {
        return messageRepository.findById(guildId).map(CommunityEventBinding::of);
    }

    public List<CommunityEventBinding> getAllBindings() {
        return messageRepository.findAll().stream().map(CommunityEventBinding::of).toList();
    }

    @Transactional
    public Optional<CommunityEventBinding> saveBinding(String guildId, String eventId, String shownEventId,
                                                       String channelId, String messageId) {
        Optional<CommunityEventMessage> existing = messageRepository.findById(guildId);
        Optional<CommunityEventBinding> previous = existing.map(CommunityEventBinding::of);
        CommunityEventMessage binding = existing.orElseGet(CommunityEventMessage::new);
        binding.setGuildId(guildId);
        binding.setEventId(eventId);
        binding.setShownEventId(shownEventId);
        binding.setChannelId(channelId);
        binding.setMessageId(messageId);
        messageRepository.save(binding);
        return previous;
    }

    @Transactional
    public boolean switchShownEvent(String guildId, String oldMessageId, String shownEventId, String messageId) {
        return messageRepository.findById(guildId)
                .filter(binding -> binding.getMessageId().equals(oldMessageId))
                .map(binding -> {
                    binding.setShownEventId(shownEventId);
                    binding.setMessageId(messageId);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public Optional<CommunityEventBinding> removeBindingIfMessage(String guildId, String messageId) {
        return messageRepository.findById(guildId)
                .filter(binding -> binding.getMessageId().equals(messageId))
                .map(binding -> {
                    messageRepository.delete(binding);
                    return CommunityEventBinding.of(binding);
                });
    }

    @Transactional
    public Optional<CommunityEventBinding> removeBinding(String guildId) {
        return messageRepository.findById(guildId).map(binding -> {
            messageRepository.delete(binding);
            return CommunityEventBinding.of(binding);
        });
    }

    @Transactional
    public List<CommunityEventBinding> removeBindingsByMessageIds(Collection<String> messageIds) {
        if (messageIds.isEmpty()) {
            return List.of();
        }
        List<CommunityEventMessage> bindings = messageRepository.findByMessageIdIn(messageIds);
        messageRepository.deleteAll(bindings);
        return bindings.stream().map(CommunityEventBinding::of).toList();
    }

    public Optional<String> getAlertChannelId(String guildId) {
        return alertConfigRepository.findById(guildId).map(CommunityEventAlertConfig::getChannelId);
    }

    public Map<String, String> getAlertChannelsByGuild() {
        return alertConfigRepository.findAll().stream()
                .collect(Collectors.toMap(CommunityEventAlertConfig::getGuildId,
                        CommunityEventAlertConfig::getChannelId));
    }

    @Transactional
    public void setAlertChannel(String guildId, String channelId) {
        CommunityEventAlertConfig config = alertConfigRepository.findById(guildId)
                .orElseGet(() -> new CommunityEventAlertConfig(guildId, channelId));
        config.setChannelId(channelId);
        alertConfigRepository.save(config);
    }

    @Transactional
    public boolean removeAlertChannel(String guildId) {
        alertStateRepository.deleteByGuildId(guildId);
        if (!alertConfigRepository.existsById(guildId)) {
            return false;
        }
        alertConfigRepository.deleteById(guildId);
        return true;
    }

    @Transactional
    public void removeAlertChannelIf(String guildId, String channelId) {
        alertConfigRepository.findById(guildId)
                .filter(config -> config.getChannelId().equals(channelId))
                .ifPresent(_ -> removeAlertChannel(guildId));
    }

    public Map<CommunityAlertType, AlertRule> getRules(String guildId) {
        return withDefaults(alertRuleRepository.findByGuildId(guildId));
    }

    public Map<String, Map<CommunityAlertType, AlertRule>> getRulesByGuild() {
        return alertRuleRepository.findAll().stream()
                .collect(Collectors.groupingBy(CommunityEventAlertRule::getGuildId,
                        Collectors.collectingAndThen(Collectors.toList(), CommunityEventService::withDefaults)));
    }

    private static Map<CommunityAlertType, AlertRule> withDefaults(List<CommunityEventAlertRule> stored) {
        Map<CommunityAlertType, AlertRule> rules = new EnumMap<>(CommunityAlertType.class);
        for (CommunityAlertType type : CommunityAlertType.values()) {
            rules.put(type, AlertRule.defaults(type));
        }
        stored.forEach(rule -> rules.put(rule.getAlertType(), AlertRule.of(rule)));
        return rules;
    }

    @Transactional
    public void updateRule(String guildId, CommunityAlertType type, Boolean enabled, String roleId,
                           boolean removeRole, Integer leadTimeMinutes) {
        CommunityEventAlertRule rule = alertRuleRepository.findByGuildIdAndAlertType(guildId, type)
                .orElseGet(() -> new CommunityEventAlertRule(guildId, type));
        if (enabled != null) {
            rule.setEnabled(enabled);
        }
        if (removeRole) {
            rule.setRoleId(null);
        } else if (roleId != null) {
            rule.setRoleId(roleId);
        }
        if (leadTimeMinutes != null && type.hasLeadTime()) {
            rule.setLeadTimeMinutes(leadTimeMinutes);
        }
        alertRuleRepository.save(rule);
    }

    @Transactional
    public void clearRole(String guildId, String roleId) {
        alertRuleRepository.clearRole(guildId, roleId);
    }

    public Map<String, Map<String, AlertProgress>> getProgressByGuild() {
        return alertStateRepository.findAll().stream()
                .collect(Collectors.groupingBy(CommunityEventAlertState::getGuildId,
                        Collectors.toMap(CommunityEventAlertState::getEventId,
                                state -> new AlertProgress(state.getLastReachedIndex(), state.getPreAlertedIndex()))));
    }

    @Transactional
    public void saveProgress(String guildId, Map<String, AlertProgress> progressByEvent) {
        if (progressByEvent.isEmpty()) {
            return;
        }
        Map<String, CommunityEventAlertState> existing = alertStateRepository.findByGuildId(guildId).stream()
                .collect(Collectors.toMap(CommunityEventAlertState::getEventId, Function.identity()));
        alertStateRepository.saveAll(progressByEvent.entrySet().stream()
                .map(entry -> {
                    AlertProgress progress = entry.getValue();
                    CommunityEventAlertState state = existing.computeIfAbsent(entry.getKey(),
                            eventId -> new CommunityEventAlertState(guildId, eventId, 0, 0));
                    state.setLastReachedIndex(progress.lastReachedIndex());
                    state.setPreAlertedIndex(progress.preAlertedIndex());
                    return state;
                })
                .toList());
    }

    private record CachedEvents(List<DiscordCommunityEvent> events, Instant fetchedAt) {
    }
}
