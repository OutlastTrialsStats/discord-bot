package com.outlasttrialsstats.discordbot.feature.setup.service;

import com.outlasttrialsstats.discordbot.feature.setup.service.GuildMessageService.ImportResult;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageFileService {

    static final int MAX_FILE_SIZE = 64 * 1024;
    private static final int MAX_REJECTED_KEYS_LENGTH = 1500;

    private final GuildMessageService guildMessageService;
    private final MessageService messageService;

    public void importFile(String guildId, Attachment file, Consumer<String> reply) {
        if (!file.getFileName().endsWith(".properties")) {
            reply.accept(messageService.getMessage(guildId, "setup.messages.invalid_file"));
            return;
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            reply.accept(messageService.getMessage(guildId, "setup.messages.file_too_large", MAX_FILE_SIZE / 1024));
            return;
        }

        file.getProxy().download().thenAccept(inputStream -> {
            try (var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                Properties properties = new Properties();
                properties.load(reader);

                ImportResult result = guildMessageService.importMessages(guildId, properties);

                log.info("Guild {} imported {} custom messages, rejected {}", guildId, result.updated(),
                        result.rejectedKeys().size());
                reply.accept(buildUploadReply(guildId, result));
            } catch (Exception e) {
                log.error("Failed to import messages for guild {}: {}", guildId, e.getMessage());
                reply.accept(messageService.getMessage(guildId, "setup.messages.upload_error"));
            }
        }).exceptionally(error -> {
            log.warn("Failed to download messages file for guild {}: {}", guildId, error.getMessage());
            reply.accept(messageService.getMessage(guildId, "setup.messages.download_error"));
            return null;
        });
    }

    private String buildUploadReply(String guildId, ImportResult result) {
        String reply = messageService.getMessage(guildId, "setup.messages.uploaded", result.updated());
        if (result.rejectedKeys().isEmpty()) {
            return reply;
        }
        return reply + "\n" + messageService.getMessage(guildId, "setup.messages.rejected",
                result.rejectedKeys().size(), formatKeys(result.rejectedKeys()));
    }

    private static String formatKeys(List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String entry = "`" + keys.get(i) + "`";
            if (sb.length() + entry.length() + 2 > MAX_REJECTED_KEYS_LENGTH) {
                sb.append(sb.isEmpty() ? "" : ", ").append("\u2026 (+").append(keys.size() - i).append(")");
                break;
            }
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(entry);
        }
        return sb.toString();
    }

    public FileUpload exportFile(String guildId) {
        byte[] bytes = guildMessageService.exportMessages(guildId).getBytes(StandardCharsets.UTF_8);
        return FileUpload.fromData(bytes, "messages.properties");
    }

    public String reset(String guildId) {
        guildMessageService.deleteAllMessages(guildId);
        log.info("Guild {} reset custom messages", guildId);
        return messageService.getMessage(guildId, "setup.messages.reset");
    }
}
