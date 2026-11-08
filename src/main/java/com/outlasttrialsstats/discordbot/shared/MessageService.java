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

    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    public String getMessage(String guildId, String key, Object... args) {
        var guildMessages = cache.computeIfAbsent(guildId, this::loadMessages);
        String customValue = guildMessages.get(key);
        Locale locale = getLocale(guildId);

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
        return guildServerRepository.findById(guildId)
                .map(GuildServer::getLanguage)
                .map(Locale::forLanguageTag)
                .orElse(Locale.ENGLISH);
    }

    public void setLanguage(String guildId, String language) {
        GuildServer server = guildServerRepository.findById(guildId)
                .orElseGet(() -> new GuildServer(guildId));
        server.setLanguage(language);
        guildServerRepository.save(server);
    }

    private Map<String, String> loadMessages(String guildId) {
        return guildMessageRepository.findByGuildId(guildId).stream()
                .collect(Collectors.toMap(GuildMessage::getMessageKey, GuildMessage::getMessageValue));
    }
}
