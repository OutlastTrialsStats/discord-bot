package com.outlasttrialsstats.discordbot.feature.setup.service;

import com.outlasttrialsstats.discordbot.entity.GuildMessage;
import com.outlasttrialsstats.discordbot.repository.GuildMessageRepository;
import com.outlasttrialsstats.discordbot.shared.GuildMessagesChangedEvent;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuildMessageService {

    public static final int MAX_MESSAGE_LENGTH = 2000;

    private static final String BUNDLE_BASE = "messages/messages";

    private final GuildMessageRepository guildMessageRepository;
    private final MessageService messageService;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, Properties> defaultsByLanguage = new ConcurrentHashMap<>();

    /**
     * Result of a custom message import.
     *
     * @param updated      number of keys whose stored custom message was created, changed or reset to the default
     * @param rejectedKeys keys that were not imported because they are unknown or their value is invalid
     */
    public record ImportResult(int updated, List<String> rejectedKeys) {
    }

    @Transactional
    public ImportResult importMessages(String guildId, Properties properties) {
        Properties defaults = getDefaults(messageService.getLocale(guildId));
        Map<String, GuildMessage> existing = guildMessageRepository.findByGuildId(guildId).stream()
                .collect(Collectors.toMap(GuildMessage::getMessageKey, m -> m));

        int updated = 0;
        List<String> rejectedKeys = new ArrayList<>();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            String value = properties.getProperty(key);
            String defaultValue = defaults.getProperty(key);

            if (defaultValue == null || !isValid(value, defaultValue)) {
                rejectedKeys.add(key);
                continue;
            }

            GuildMessage message = existing.get(key);
            if (value.equals(defaultValue)) {
                if (message != null) {
                    guildMessageRepository.delete(message);
                    updated++;
                }
            } else if (message == null) {
                guildMessageRepository.save(new GuildMessage(guildId, key, value));
                updated++;
            } else if (!value.equals(message.getMessageValue())) {
                message.setMessageValue(value);
                updated++;
            }
        }

        eventPublisher.publishEvent(new GuildMessagesChangedEvent(guildId));
        return new ImportResult(updated, List.copyOf(rejectedKeys));
    }

    public String exportMessages(String guildId) {
        Map<String, String> customMessages = guildMessageRepository.findByGuildId(guildId).stream()
                .collect(Collectors.toMap(GuildMessage::getMessageKey, GuildMessage::getMessageValue));

        Properties defaults = getDefaults(messageService.getLocale(guildId));
        Properties export = new Properties();
        for (String key : defaults.stringPropertyNames()) {
            export.setProperty(key, customMessages.getOrDefault(key, defaults.getProperty(key)));
        }

        StringWriter writer = new StringWriter();
        try {
            export.store(writer, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Properties.store escapes every entry onto a single line; drop the date comment and sort for a stable file
        return writer.toString().lines()
                .filter(line -> !line.startsWith("#"))
                .sorted()
                .collect(Collectors.joining("\n", "", "\n"));
    }

    @Transactional
    public void deleteAllMessages(String guildId) {
        guildMessageRepository.deleteByGuildId(guildId);
        eventPublisher.publishEvent(new GuildMessagesChangedEvent(guildId));
    }

    private static boolean isValid(String value, String defaultValue) {
        if (value.isBlank() || value.length() > MAX_MESSAGE_LENGTH) {
            return false;
        }
        try {
            int arguments = new MessageFormat(value).getFormatsByArgumentIndex().length;
            int defaultArguments = new MessageFormat(defaultValue).getFormatsByArgumentIndex().length;
            return arguments <= defaultArguments;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Returns the raw (unformatted) default message patterns for the given locale, falling back to the
     * base bundle for keys the locale does not translate.
     */
    private Properties getDefaults(Locale locale) {
        return defaultsByLanguage.computeIfAbsent(locale.getLanguage(), language -> {
            Properties base = load(BUNDLE_BASE + ".properties");
            ClassPathResource localized = new ClassPathResource(BUNDLE_BASE + "_" + language + ".properties");
            if (language.isEmpty() || !localized.exists()) {
                return base;
            }
            Properties merged = new Properties();
            merged.putAll(base);
            merged.putAll(load(localized.getPath()));
            return merged;
        });
    }

    private static Properties load(String path) {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource(path).getInputStream();
             var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load default messages from " + path, e);
        }
        return properties;
    }
}
