package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.feature.setup.service.GuildMessageService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

        file.getProxy().download().thenAccept(inputStream -> {
            try (var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                Properties properties = new Properties();
                properties.load(reader);

                int count = guildMessageService.importMessages(guildId, properties);

                log.info("Guild {} imported {} custom messages", guildId, count);
                hook.editOriginal(messageService.getMessage(guildId, "setup.messages.uploaded", count)).queue();
            } catch (Exception e) {
                log.error("Failed to import messages for guild {}: {}", guildId, e.getMessage());
                hook.editOriginal(messageService.getMessage(guildId, "setup.messages.upload_error")).queue();
            }
        });
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
