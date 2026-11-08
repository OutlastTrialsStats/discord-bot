package com.outlasttrialsstats.discordbot.feature.setup.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.feature.setup.service.GuildMessageService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.NamedAttachmentProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessagesCommandTest {

    private static final String GUILD_ID = "guild-1";

    @Mock
    private GuildMessageService guildMessageService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessagesCommand messagesCommand;

    @Mock
    private SlashCommandInteractionEvent event;

    @Mock
    private Attachment attachment;

    @Mock
    private NamedAttachmentProxy proxy;

    @Mock
    private InteractionHook hook;

    @Mock
    private WebhookMessageEditAction<Message> editAction;

    @BeforeEach
    void setUp() {
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn(GUILD_ID);
        when(event.getGuild()).thenReturn(guild);
        OptionMapping fileOption = mock(OptionMapping.class);
        when(fileOption.getAsAttachment()).thenReturn(attachment);
        when(event.getOption("file")).thenReturn(fileOption);
        when(event.deferReply(true)).thenReturn(mock(ReplyCallbackAction.class));
        when(event.getHook()).thenReturn(hook);
        when(attachment.getFileName()).thenReturn("messages.properties");
        when(attachment.getProxy()).thenReturn(proxy);
        when(hook.editOriginal(any(String.class))).thenReturn(editAction);
    }

    @Test
    void onUpload_downloadFails_repliesWithLocalizedError() {
        when(proxy.download()).thenReturn(CompletableFuture.failedFuture(new IOException("connection reset")));
        when(messageService.getMessage(GUILD_ID, "setup.messages.download_error")).thenReturn("download failed");

        messagesCommand.onUpload(event);

        verify(hook).editOriginal("download failed");
        verify(guildMessageService, never()).importMessages(any(), any());
    }

    @Test
    void onUpload_downloadSucceeds_importsMessages() {
        InputStream content = new ByteArrayInputStream("a.b=c".getBytes(StandardCharsets.UTF_8));
        when(proxy.download()).thenReturn(CompletableFuture.completedFuture(content));
        when(guildMessageService.importMessages(any(), any())).thenReturn(1);
        when(messageService.getMessage(GUILD_ID, "setup.messages.uploaded", 1)).thenReturn("uploaded");

        messagesCommand.onUpload(event);

        verify(hook).editOriginal("uploaded");
    }
}
