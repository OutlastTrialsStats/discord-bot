package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.feature.setup.service.BasicSetupService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import io.github.kaktushose.jdac.annotations.interactions.Command;
import io.github.kaktushose.jdac.annotations.interactions.CommandConfig;
import io.github.kaktushose.jdac.annotations.interactions.Interaction;
import io.github.kaktushose.jdac.dispatching.events.interactions.CommandEvent;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.stereotype.Component;

@Interaction
@Component
@RequiredArgsConstructor
public class StartBasicCommand {

    private final BasicSetupService basicSetupService;
    private final MessageService messageService;

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command("setup start")
    public void onStartBasic(CommandEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();

        // Defer the reply so we can edit it later from async callbacks
        event.jdaEvent().deferReply(true).queue();
        InteractionHook hook = event.jdaEvent().getHook();

        hook.editOriginal(messageService.getMessage(guildId, "setup.basic.starting")).queue();

        basicSetupService.setupAllRoles(guild,
                (count, roles) -> hook.editOriginal(
                        messageService.getMessage(guildId, "setup.basic.completed", count, String.join(", ", roles))
                ).queue(),
                () -> hook.editOriginal(
                        messageService.getMessage(guildId, "setup.basic.no_changes")
                ).queue()
        );
    }
}
