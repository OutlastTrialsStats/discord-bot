package com.outlasttrialsstats.discordbot.feature.setup.service;

import com.outlasttrialsstats.discordbot.entity.GuildMessage;
import com.outlasttrialsstats.discordbot.repository.GuildMessageRepository;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuildMessageService {

    private final GuildMessageRepository guildMessageRepository;
    private final MessageSource messageSource;
    private final MessageService messageService;

    @Transactional
    public int importMessages(String guildId, Properties properties) {
        Map<String, GuildMessage> existing = guildMessageRepository.findByGuildId(guildId).stream()
                .collect(Collectors.toMap(GuildMessage::getMessageKey, m -> m));

        int count = 0;
        for (var entry : properties.entrySet()) {
            String key = entry.getKey().toString();
            String value = entry.getValue().toString();

            GuildMessage message = existing.get(key);
            if (message != null) {
                message.setMessageValue(value);
            } else {
                guildMessageRepository.save(new GuildMessage(guildId, key, value));
            }
            count++;
        }

        messageService.invalidateCache(guildId);
        return count;
    }

    public String exportMessages(String guildId) {
        Map<String, String> customMessages = guildMessageRepository.findByGuildId(guildId).stream()
                .collect(Collectors.toMap(GuildMessage::getMessageKey, GuildMessage::getMessageValue));

        Properties defaultProperties = loadDefaultProperties();
        StringBuilder sb = new StringBuilder();

        for (String key : defaultProperties.stringPropertyNames().stream().sorted().toList()) {
            String value = customMessages.getOrDefault(key,
                    messageSource.getMessage(key, null, Locale.ENGLISH));
            sb.append(key).append("=").append(value).append("\n");
        }
        return sb.toString();
    }

    @Transactional
    public void deleteAllMessages(String guildId) {
        guildMessageRepository.deleteByGuildId(guildId);
        messageService.invalidateCache(guildId);
    }

    private Properties loadDefaultProperties() {
        Properties properties = new Properties();
        try (var reader = new InputStreamReader(new PathMatchingResourcePatternResolver()
                .getResource("classpath:messages/messages.properties").getInputStream(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load default messages", e);
        }
        return properties;
    }
}
