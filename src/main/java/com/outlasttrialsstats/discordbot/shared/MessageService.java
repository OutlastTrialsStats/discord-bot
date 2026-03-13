package com.outlasttrialsstats.discordbot.shared;

import com.outlasttrialsstats.discordbot.entity.GuildMessage;
import com.outlasttrialsstats.discordbot.entity.GuildSettings;
import com.outlasttrialsstats.discordbot.repository.GuildMessageRepository;
import com.outlasttrialsstats.discordbot.repository.GuildSettingsRepository;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageSource messageSource;
    private final GuildSettingsRepository guildSettingsRepository;
    private final GuildMessageRepository guildMessageRepository;

    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    public String getMessage(String guildId, String key, Object... args) {
        var guildMessages = cache.computeIfAbsent(guildId, this::loadMessages);
        String customValue = guildMessages.get(key);

        if (customValue != null) {
            return MessageFormat.format(customValue, args);
        }

        Locale locale = getLocale(guildId);
        return messageSource.getMessage(key, args, locale);
    }

    public void invalidateCache(String guildId) {
        cache.remove(guildId);
    }

    public Locale getLocale(String guildId) {
        return guildSettingsRepository.findById(guildId)
                .map(GuildSettings::getLanguage)
                .map(Locale::forLanguageTag)
                .orElse(Locale.ENGLISH);
    }

    public void setLanguage(String guildId, String language) {
        GuildSettings settings = guildSettingsRepository.findById(guildId)
                .orElseGet(() -> new GuildSettings(guildId));
        settings.setLanguage(language);
        guildSettingsRepository.save(settings);
    }

    private Map<String, String> loadMessages(String guildId) {
        return guildMessageRepository.findByGuildId(guildId).stream()
                .collect(Collectors.toMap(GuildMessage::getMessageKey, GuildMessage::getMessageValue));
    }
}
