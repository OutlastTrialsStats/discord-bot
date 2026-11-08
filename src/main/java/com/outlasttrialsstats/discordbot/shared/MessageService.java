package com.outlasttrialsstats.discordbot.shared;

import com.outlasttrialsstats.discordbot.entity.GuildMessage;
import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.repository.GuildMessageRepository;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageSource messageSource;
    private final GuildServerRepository guildServerRepository;
    private final GuildMessageRepository guildMessageRepository;

    /** What is cached per guild: its language and its custom messages. */
    private record GuildSettings(Locale locale, Map<String, String> customMessages) {
    }

    private final Map<String, GuildSettings> cache = new ConcurrentHashMap<>();

    public String getMessage(String guildId, String key, Object... args) {
        GuildSettings settings = settings(guildId);
        String customValue = settings.customMessages().get(key);
        Locale locale = settings.locale();

        if (customValue != null) {
            try {
                return new MessageFormat(customValue, locale).format(args);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid custom message '{}' for guild {}, using default: {}", key, guildId, e.getMessage());
            }
        }

        return messageSource.getMessage(key, args, locale);
    }

    public void invalidateCache(String guildId) {
        cache.remove(guildId);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGuildMessagesChanged(GuildMessagesChangedEvent event) {
        invalidateCache(event.guildId());
    }

    public Locale getLocale(String guildId) {
        return settings(guildId).locale();
    }

    public void setLanguage(String guildId, String language) {
        GuildServer server = guildServerRepository.findById(guildId)
                .orElseGet(() -> new GuildServer(guildId));
        server.setLanguage(language);
        guildServerRepository.save(server);
        invalidateCache(guildId);
    }

    private GuildSettings settings(String guildId) {
        return cache.computeIfAbsent(guildId, this::loadSettings);
    }

    private GuildSettings loadSettings(String guildId) {
        Locale locale = guildServerRepository.findById(guildId)
                .map(GuildServer::getLanguage)
                .map(Locale::forLanguageTag)
                .orElse(Locale.ENGLISH);
        Map<String, String> customMessages = guildMessageRepository.findByGuildId(guildId).stream()
                .collect(Collectors.toMap(GuildMessage::getMessageKey, GuildMessage::getMessageValue));
        return new GuildSettings(locale, customMessages);
    }
}
