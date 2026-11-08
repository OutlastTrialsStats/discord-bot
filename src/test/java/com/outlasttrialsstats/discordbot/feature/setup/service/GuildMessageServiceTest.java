package com.outlasttrialsstats.discordbot.feature.setup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.entity.GuildMessage;
import com.outlasttrialsstats.discordbot.feature.setup.service.GuildMessageService.ImportResult;
import com.outlasttrialsstats.discordbot.repository.GuildMessageRepository;
import com.outlasttrialsstats.discordbot.shared.GuildMessagesChangedEvent;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class GuildMessageServiceTest {

    private static final String GUILD_ID = "guild-1";

    @Mock
    private GuildMessageRepository guildMessageRepository;

    @Mock
    private MessageService messageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GuildMessageService guildMessageService;

    @BeforeEach
    void setUp() {
        lenient().when(messageService.getLocale(GUILD_ID)).thenReturn(Locale.ENGLISH);
    }

    private static Properties properties(String... keyValues) {
        Properties properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return properties;
    }

    private static Properties parse(String content) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(content));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return properties;
    }

    @Test
    void importMessages_validValue_isSavedAndCacheInvalidationPublished() {
        when(guildMessageRepository.findByGuildId(GUILD_ID)).thenReturn(List.of());

        ImportResult result = guildMessageService.importMessages(GUILD_ID,
                properties("setup.language.success", "Now speaking **{0}**!"));

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.rejectedKeys()).isEmpty();
        ArgumentCaptor<GuildMessage> saved = ArgumentCaptor.forClass(GuildMessage.class);
        verify(guildMessageRepository).save(saved.capture());
        assertThat(saved.getValue().getMessageKey()).isEqualTo("setup.language.success");
        assertThat(saved.getValue().getMessageValue()).isEqualTo("Now speaking **{0}**!");
        verify(eventPublisher).publishEvent(new GuildMessagesChangedEvent(GUILD_ID));
        verify(messageService, never()).invalidateCache(any());
    }

    @Test
    void importMessages_invalidEntries_areRejected() {
        when(guildMessageRepository.findByGuildId(GUILD_ID)).thenReturn(List.of());

        ImportResult result = guildMessageService.importMessages(GUILD_ID, properties(
                "unknown.key", "Hello",
                "setup.language.success", "Broken {0",
                "profile.no_changes", "Too many {0}",
                "sync.cooldown", "x".repeat(GuildMessageService.MAX_MESSAGE_LENGTH + 1),
                "leaderboard.empty", " "));

        assertThat(result.updated()).isZero();
        assertThat(result.rejectedKeys()).containsExactly(
                "leaderboard.empty", "profile.no_changes", "setup.language.success", "sync.cooldown", "unknown.key");
        verify(guildMessageRepository, never()).save(any());
    }

    @Test
    void importMessages_fewerPlaceholdersThanDefault_isAccepted() {
        when(guildMessageRepository.findByGuildId(GUILD_ID)).thenReturn(List.of());

        ImportResult result = guildMessageService.importMessages(GUILD_ID,
                properties("sync.completed", "Done, {0} updated."));

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.rejectedKeys()).isEmpty();
    }

    @Test
    void importMessages_valueEqualToDefault_isNotStoredAndRemovesExistingOverride() {
        GuildMessage existing = new GuildMessage(GUILD_ID, "profile.no_changes", "Custom");
        when(guildMessageRepository.findByGuildId(GUILD_ID)).thenReturn(List.of(existing));

        ImportResult result = guildMessageService.importMessages(GUILD_ID, properties(
                "profile.no_changes", "Your roles are already up to date.",
                "leaderboard.empty", "No entries found."));

        assertThat(result.updated()).isEqualTo(1);
        verify(guildMessageRepository).delete(existing);
        verify(guildMessageRepository, never()).save(any());
    }

    @Test
    void importMessages_unchangedCustomValue_isNotCounted() {
        GuildMessage existing = new GuildMessage(GUILD_ID, "profile.no_changes", "Custom");
        when(guildMessageRepository.findByGuildId(GUILD_ID)).thenReturn(List.of(existing));

        ImportResult result = guildMessageService.importMessages(GUILD_ID, properties("profile.no_changes", "Custom"));

        assertThat(result.updated()).isZero();
        assertThat(existing.getMessageValue()).isEqualTo("Custom");
    }

    @Test
    void exportMessages_usesGuildLocaleAndEscapesValues() {
        when(messageService.getLocale(GUILD_ID)).thenReturn(Locale.GERMAN);
        when(guildMessageRepository.findByGuildId(GUILD_ID)).thenReturn(List.of(
                new GuildMessage(GUILD_ID, "profile.no_changes", "Zeile 1\nZeile 2 = ok")));

        String export = guildMessageService.exportMessages(GUILD_ID);

        assertThat(export.lines()).noneMatch(line -> line.startsWith("#"));
        assertThat(export.lines().toList()).isSorted();
        Properties parsed = parse(export);
        assertThat(parsed.getProperty("profile.no_changes")).isEqualTo("Zeile 1\nZeile 2 = ok");
        assertThat(parsed.getProperty("profile.updated")).startsWith("Profil aktualisiert!");
        assertThat(parsed.getProperty("error.missing_permission.manage_roles")).startsWith("Ich habe keine");
    }

    @Test
    void exportMessages_keepsDefaultPatternsUnformatted() {
        when(guildMessageRepository.findByGuildId(GUILD_ID)).thenReturn(List.of());

        Properties parsed = parse(guildMessageService.exportMessages(GUILD_ID));

        assertThat(parsed.getProperty("error.missing_permission.manage_roles")).startsWith("I don''t have");
        assertThat(parsed.getProperty("setup.language.success")).contains("{0}");
    }

    @Test
    void exportThenImport_unchangedFile_storesNothing() {
        when(guildMessageRepository.findByGuildId(GUILD_ID)).thenReturn(List.of());
        Properties parsed = parse(guildMessageService.exportMessages(GUILD_ID));

        ImportResult result = guildMessageService.importMessages(GUILD_ID, parsed);

        assertThat(result.updated()).isZero();
        assertThat(result.rejectedKeys()).isEmpty();
        verify(guildMessageRepository, never()).save(any());
    }

    @Test
    void deleteAllMessages_publishesCacheInvalidation() {
        guildMessageService.deleteAllMessages(GUILD_ID);

        verify(guildMessageRepository).deleteByGuildId(GUILD_ID);
        verify(eventPublisher).publishEvent(new GuildMessagesChangedEvent(GUILD_ID));
    }
}
