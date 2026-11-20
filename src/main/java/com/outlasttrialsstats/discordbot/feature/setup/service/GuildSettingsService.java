package com.outlasttrialsstats.discordbot.feature.setup.service;

import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GuildSettingsService {

    public static final String[] LANGUAGES = {"en", "de"};

    private final GuildServerRepository guildServerRepository;
    private final MessageService messageService;

    public GuildServer get(String guildId) {
        return guildServerRepository.findById(guildId).orElseGet(() -> new GuildServer(guildId));
    }

    public String setAutoNickname(Guild guild, boolean enabled) {
        String guildId = guild.getId();
        if (enabled && !guild.getSelfMember().hasPermission(Permission.NICKNAME_MANAGE)) {
            return messageService.getMessage(guildId, "error.missing_permission.manage_nicknames");
        }
        update(guildId, server -> server.setAutoNickname(enabled));
        return messageService.getMessage(guildId, enabled ? "setup.nickname.enabled" : "setup.nickname.disabled");
    }

    public String setUserCommandsVisible(String guildId, boolean visible) {
        update(guildId, server -> server.setUserCommandsVisible(visible));
        return messageService.getMessage(guildId, visible
                ? "setup.toggle_user_command_visibility.enabled"
                : "setup.toggle_user_command_visibility.disabled");
    }

    public String setLanguage(String guildId, String language) {
        messageService.setLanguage(guildId, language);
        return messageService.getMessage(guildId, "setup.language.success", languageName(language));
    }

    public static String languageName(String language) {
        return "de".equals(language) ? "Deutsch" : "English";
    }

    private void update(String guildId, Consumer<GuildServer> change) {
        GuildServer server = get(guildId);
        change.accept(server);
        guildServerRepository.save(server);
    }
}
