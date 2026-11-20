package com.outlasttrialsstats.discordbot.feature.setup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.feature.setup.service.GuildMessageService.ImportResult;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.NamedAttachmentProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageFileServiceTest {

    private static final String GUILD_ID = "guild-1";

    @Mock
    private GuildMessageService guildMessageService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageFileService messageFileService;

    @Mock
    private Attachment attachment;

    @Mock
    private NamedAttachmentProxy proxy;

    @Mock
    private Consumer<String> reply;

    @Test
    void importFile_downloadFails_repliesWithLocalizedError() {
        givenPropertiesFile();
        when(proxy.download()).thenReturn(CompletableFuture.failedFuture(new IOException("connection reset")));
        when(messageService.getMessage(GUILD_ID, "setup.messages.download_error")).thenReturn("download failed");

        messageFileService.importFile(GUILD_ID, attachment, reply);

        verify(reply).accept("download failed");
        verify(guildMessageService, never()).importMessages(any(), any());
    }

    @Test
    void importFile_downloadSucceeds_importsMessages() {
        givenPropertiesFile();
        InputStream content = new ByteArrayInputStream("a.b=c".getBytes(StandardCharsets.UTF_8));
        when(proxy.download()).thenReturn(CompletableFuture.completedFuture(content));
        when(guildMessageService.importMessages(any(), any())).thenReturn(new ImportResult(1, List.of()));
        when(messageService.getMessage(GUILD_ID, "setup.messages.uploaded", 1)).thenReturn("uploaded");

        messageFileService.importFile(GUILD_ID, attachment, reply);

        verify(reply).accept("uploaded");
    }

    @Test
    void importFile_rejectedKeys_areReported() {
        givenPropertiesFile();
        InputStream content = new ByteArrayInputStream("a.b=c".getBytes(StandardCharsets.UTF_8));
        when(proxy.download()).thenReturn(CompletableFuture.completedFuture(content));
        when(guildMessageService.importMessages(any(), any()))
                .thenReturn(new ImportResult(0, List.of("a.b", "c.d")));
        when(messageService.getMessage(GUILD_ID, "setup.messages.uploaded", 0)).thenReturn("uploaded");
        when(messageService.getMessage(GUILD_ID, "setup.messages.rejected", 2, "`a.b`, `c.d`")).thenReturn("rejected");

        messageFileService.importFile(GUILD_ID, attachment, reply);

        verify(reply).accept("uploaded\nrejected");
    }

    @Test
    void importFile_fileTooLarge_isRejectedWithoutDownload() {
        givenPropertiesFile();
        when(attachment.getSize()).thenReturn(MessageFileService.MAX_FILE_SIZE + 1);
        when(messageService.getMessage(GUILD_ID, "setup.messages.file_too_large", 64)).thenReturn("too large");

        messageFileService.importFile(GUILD_ID, attachment, reply);

        verify(reply).accept("too large");
        verify(proxy, never()).download();
        verify(guildMessageService, never()).importMessages(any(), any());
    }

    @Test
    void importFile_wrongExtension_isRejected() {
        when(attachment.getFileName()).thenReturn("messages.txt");
        when(messageService.getMessage(GUILD_ID, "setup.messages.invalid_file")).thenReturn("invalid");

        messageFileService.importFile(GUILD_ID, attachment, reply);

        verify(reply).accept("invalid");
        verify(guildMessageService, never()).importMessages(any(), any());
    }

    @Test
    void exportFile_containsCurrentMessages() throws IOException {
        when(guildMessageService.exportMessages(GUILD_ID)).thenReturn("a.b=c");

        try (FileUpload file = messageFileService.exportFile(GUILD_ID)) {
            assertThat(file.getName()).isEqualTo("messages.properties");
            assertThat(new String(file.getData().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("a.b=c");
        }
    }

    @Test
    void reset_deletesCustomMessages() {
        when(messageService.getMessage(GUILD_ID, "setup.messages.reset")).thenReturn("reset");

        assertThat(messageFileService.reset(GUILD_ID)).isEqualTo("reset");
        verify(guildMessageService).deleteAllMessages(GUILD_ID);
    }

    private void givenPropertiesFile() {
        when(attachment.getFileName()).thenReturn("messages.properties");
        lenient().when(attachment.getProxy()).thenReturn(proxy);
    }
}
