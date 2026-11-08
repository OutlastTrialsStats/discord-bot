package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.feature.setup.service.GuildMessageService;
import com.outlasttrialsstats.discordbot.feature.setup.service.GuildMessageService.ImportResult;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessagesCommand {

    static final int MAX_FILE_SIZE = 64 * 1024;
    private static final int MAX_REJECTED_KEYS_LENGTH = 1500;

    private final GuildMessageService guildMessageService;
    private final MessageService messageService;

    public void onUpload(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Attachment file = event.getOption("file").getAsAttachment();

        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        if (!file.getFileName().endsWith(".properties")) {
            hook.editOriginal(messageService.getMessage(guildId, "setup.messages.invalid_file")).queue();
            return;
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            hook.editOriginal(messageService.getMessage(guildId, "setup.messages.file_too_large", MAX_FILE_SIZE / 1024))
                    .queue();
            return;
        }

        file.getProxy().download().thenAccept(inputStream -> {
            try (var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                Properties properties = new Properties();
                properties.load(reader);

                ImportResult result = guildMessageService.importMessages(guildId, properties);

                log.info("Guild {} imported {} custom messages, rejected {}", guildId, result.updated(),
                        result.rejectedKeys().size());
                hook.editOriginal(buildUploadReply(guildId, result)).queue();
            } catch (Exception e) {
                log.error("Failed to import messages for guild {}: {}", guildId, e.getMessage());
                hook.editOriginal(messageService.getMessage(guildId, "setup.messages.upload_error")).queue();
            }
        }).exceptionally(error -> {
            log.warn("Failed to download messages file for guild {}: {}", guildId, error.getMessage());
            hook.editOriginal(messageService.getMessage(guildId, "setup.messages.download_error")).queue();
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

    public void onDownload(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        String content = guildMessageService.exportMessages(guildId);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        hook.editOriginal("").setAttachments(
                FileUpload.fromData(bytes, "messages.properties")
        ).queue();
    }

    public void onReset(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        guildMessageService.deleteAllMessages(guildId);

        log.info("Guild {} reset custom messages", guildId);
        event.reply(messageService.getMessage(guildId, "setup.messages.reset"))
                .setEphemeral(true).queue();
    }
}
