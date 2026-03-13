package com.outlasttrialsstats.discordbot.shared;

import com.outlasttrialsstats.discordbot.entity.GuildSettings;
import com.outlasttrialsstats.discordbot.repository.GuildSettingsRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageSource messageSource;
    private final GuildSettingsRepository guildSettingsRepository;

    public String getMessage(String guildId, String key, Object... args) {
        Locale locale = getLocale(guildId);
        return messageSource.getMessage(key, args, locale);
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
}
