package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.feature.setup.service.GuildMessageService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import io.github.kaktushose.jdac.annotations.interactions.Command;
import io.github.kaktushose.jdac.annotations.interactions.CommandConfig;
import io.github.kaktushose.jdac.annotations.interactions.Interaction;
import io.github.kaktushose.jdac.annotations.interactions.Param;
import io.github.kaktushose.jdac.dispatching.events.interactions.CommandEvent;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.stereotype.Component;

@Interaction
@Component
@RequiredArgsConstructor
@Slf4j
public class MessagesCommand {

    private final GuildMessageService guildMessageService;
    private final MessageService messageService;

    @CommandConfig(enabledFor = Permission.ADMINISTRATOR)
    @Command(value = "setup messages-upload", desc = "Upload custom bot messages from a properties file")
    public void onUpload(CommandEvent event, @Param("Properties file with custom messages") Attachment file) {
        String guildId = event.getGuild().getId();

        event.jdaEvent().deferReply(true).queue();
        InteractionHook hook = event.jdaEvent().getHook();

        if (!file.getFileName().endsWith(".properties")) {
            hook.editOriginal(messageService.getMessage(guildId, "setup.messages.invalid_file")).queue();
            return;
        }

        file.getProxy().download().thenAccept(inputStream -> {
            try (InputStream is = inputStream) {
                Properties properties = new Properties();
                properties.load(is);

                int count = guildMessageService.importMessages(guildId, properties);

                log.info("Guild {} imported {} custom messages", guildId, count);
                hook.editOriginal(messageService.getMessage(guildId, "setup.messages.uploaded", count)).queue();
            } catch (Exception e) {
                log.error("Failed to import messages for guild {}: {}", guildId, e.getMessage());
                hook.editOriginal(messageService.getMessage(guildId, "setup.messages.upload_error")).queue();
            }
        });
    }

    @CommandConfig(enabledFor = Permission.ADMINISTRATOR)
    @Command(value = "setup messages-download", desc = "Download current bot messages as a properties file")
    public void onDownload(CommandEvent event) {
        String guildId = event.getGuild().getId();

        event.jdaEvent().deferReply(true).queue();
        InteractionHook hook = event.jdaEvent().getHook();

        String content = guildMessageService.exportMessages(guildId);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        hook.editOriginal("").setAttachments(
                FileUpload.fromData(bytes, "messages.properties")
        ).queue();
    }

    @CommandConfig(enabledFor = Permission.ADMINISTRATOR)
    @Command(value = "setup messages-reset", desc = "Reset all custom messages to defaults")
    public void onReset(CommandEvent event) {
        String guildId = event.getGuild().getId();

        guildMessageService.deleteAllMessages(guildId);

        log.info("Guild {} reset custom messages", guildId);
        event.with().ephemeral(true)
                .reply(messageService.getMessage(guildId, "setup.messages.reset"));
    }
}
