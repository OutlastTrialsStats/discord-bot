package com.outlasttrialsstats.discordbot.command;

import com.outlasttrialsstats.discordbot.feature.communityevent.command.CommunityEventCommand;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommandRegistrar {

    private final JDA jda;

    @EventListener(ApplicationReadyEvent.class)
    public void registerCommands() {
        jda.updateCommands().addCommands(
                Commands.slash("leaderboard", "View the leaderboard for a stat category")
                        .addOptions(new OptionData(OptionType.STRING, "category", "Statistic category", true)
                                .setAutoComplete(true)),

                Commands.slash(CommunityEventCommand.NAME, "Show the progress of the current community event")
                        .setContexts(InteractionContextType.GUILD)
                        .addOptions(new OptionData(OptionType.STRING, CommunityEventCommand.OPTION,
                                "Event to show (leave empty for the current event)", false)
                                .setAutoComplete(true)),

                Commands.slash("sync-profile", "Sync your roles based on your Outlast Trials stats")
                        .setContexts(InteractionContextType.GUILD),

                Commands.slash("sync-all", "Sync roles for all members in this server")
                        .setContexts(InteractionContextType.GUILD)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES)),

                Commands.slash("setup", "Open the setup panel for this server")
                        .setContexts(InteractionContextType.GUILD)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
        ).queue();
    }
}
