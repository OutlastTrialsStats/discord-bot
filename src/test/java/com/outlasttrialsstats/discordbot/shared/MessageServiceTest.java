package com.outlasttrialsstats.discordbot.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.entity.GuildMessage;
import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.repository.GuildMessageRepository;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    private static final String GUILD_ID = "guild-1";

    @Mock
    private GuildServerRepository guildServerRepository;

    @Mock
    private GuildMessageRepository guildMessageRepository;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        // same settings as spring.messages in application.yml
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages/messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setAlwaysUseMessageFormat(true);
        messageService = new MessageService(messageSource, guildServerRepository, guildMessageRepository);
    }

    private void guildLanguage(String language) {
        GuildServer server = new GuildServer(GUILD_ID);
        server.setLanguage(language);
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.of(server));
    }

    private void customMessages(GuildMessage... messages) {
        when(guildMessageRepository.findByGuildId(GUILD_ID)).thenReturn(List.of(messages));
    }

    @Test
    void getMessage_defaultWithApostrophe_isUnescaped() {
        guildLanguage("en");
        customMessages();

        String message = messageService.getMessage(GUILD_ID, "error.missing_permission.manage_roles");

        assertThat(message).startsWith("I don't have the **Manage Roles** permission");
    }

    @Test
    void getMessage_channelWrite_replacesChannelPlaceholder() {
        guildLanguage("en");
        customMessages();

        String message = messageService.getMessage(GUILD_ID, "error.missing_permission.channel_write", "#general");

        assertThat(message).startsWith("I am missing permissions in #general.");
    }

    @Test
    void getMessage_customValue_isFormattedWithGuildLocale() {
        guildLanguage("de");
        customMessages(new GuildMessage(GUILD_ID, "sync.completed", "Fertig: {0} aktualisiert, ''{1}'' gleich"));

        String message = messageService.getMessage(GUILD_ID, "sync.completed", 1234, 5, 0, 0);

        assertThat(message).isEqualTo("Fertig: 1.234 aktualisiert, '5' gleich");
    }

    @Test
    void getMessage_customValueWithoutArguments_unescapesApostrophes() {
        guildLanguage("en");
        customMessages(new GuildMessage(GUILD_ID, "profile.no_changes", "Nothing to do, you''re fine."));

        assertThat(messageService.getMessage(GUILD_ID, "profile.no_changes")).isEqualTo("Nothing to do, you're fine.");
    }

    @Test
    void getMessage_invalidCustomValue_fallsBackToDefault() {
        guildLanguage("en");
        customMessages(new GuildMessage(GUILD_ID, "setup.language.success", "Broken {0"));

        String message = messageService.getMessage(GUILD_ID, "setup.language.success", "de");

        assertThat(message).isEqualTo("Language has been set to **de**.");
    }

    @Test
    void getMessage_germanGuild_usesGermanDefault() {
        guildLanguage("de");
        customMessages();

        assertThat(messageService.getMessage(GUILD_ID, "profile.no_changes")).isEqualTo("Deine Rollen sind bereits aktuell.");
    }

    @Test
    void onGuildMessagesChanged_invalidatesCache() {
        guildLanguage("en");
        customMessages();

        messageService.getMessage(GUILD_ID, "profile.no_changes");
        messageService.getMessage(GUILD_ID, "profile.no_changes");
        verify(guildMessageRepository, times(1)).findByGuildId(GUILD_ID);

        messageService.onGuildMessagesChanged(new GuildMessagesChangedEvent(GUILD_ID));
        messageService.getMessage(GUILD_ID, "profile.no_changes");

        verify(guildMessageRepository, times(2)).findByGuildId(GUILD_ID);
    }

    @Test
    void getMessage_repeatedCalls_loadLocaleAndCustomMessagesOnlyOnce() {
        guildLanguage("en");
        customMessages();

        messageService.getMessage(GUILD_ID, "profile.no_changes");
        messageService.getMessage(GUILD_ID, "profile.no_changes");
        messageService.getLocale(GUILD_ID);

        verify(guildServerRepository, times(1)).findById(GUILD_ID);
        verify(guildMessageRepository, times(1)).findByGuildId(GUILD_ID);
    }

    @Test
    void onGuildMessagesChanged_reloadsLocale() {
        guildLanguage("en");
        customMessages();
        messageService.getMessage(GUILD_ID, "profile.no_changes");

        guildLanguage("de");
        messageService.onGuildMessagesChanged(new GuildMessagesChangedEvent(GUILD_ID));

        assertThat(messageService.getMessage(GUILD_ID, "profile.no_changes")).isEqualTo("Deine Rollen sind bereits aktuell.");
        verify(guildServerRepository, times(2)).findById(GUILD_ID);
    }

    @Test
    void setLanguage_invalidatesCachedLocale() {
        guildLanguage("en");
        customMessages();
        assertThat(messageService.getLocale(GUILD_ID)).isEqualTo(java.util.Locale.ENGLISH);

        messageService.setLanguage(GUILD_ID, "de");

        assertThat(messageService.getMessage(GUILD_ID, "profile.no_changes")).isEqualTo("Deine Rollen sind bereits aktuell.");
        assertThat(messageService.getLocale(GUILD_ID)).isEqualTo(java.util.Locale.GERMAN);
    }

    @Test
    void getLocale_noGuildServer_defaultsToEnglish() {
        when(guildServerRepository.findById(GUILD_ID)).thenReturn(Optional.empty());
        customMessages();

        assertThat(messageService.getLocale(GUILD_ID)).isEqualTo(java.util.Locale.ENGLISH);
    }
}
