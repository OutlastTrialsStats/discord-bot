package com.outlasttrialsstats.discordbot.feature.setup.panel;

import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventSettings;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardSetupService;
import com.outlasttrialsstats.discordbot.feature.setup.command.StartBasicCommand;
import com.outlasttrialsstats.discordbot.feature.setup.service.GuildSettingsService;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HomeSection implements PanelSection {

    static final String ID = "home";

    private final PanelSupport support;
    private final RoleMappingService roleMappingService;
    private final CommunityEventSettings eventSettings;
    private final LeaderboardSetupService leaderboardSetupService;
    private final GuildSettingsService settingsService;
    private final StartBasicCommand startBasicCommand;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public PanelView render(Guild guild) {
        String guildId = guild.getId();
        GuildServer server = settingsService.get(guildId);
        String alerts = eventSettings.alertChannelId(guildId)
                .map(id -> "<#" + id + ">")
                .orElseGet(() -> support.onOff(guildId, false));

        var embed = support.embed(support.message(guildId, "setup.panel.home.title"))
                .setDescription(support.message(guildId, "setup.panel.home.description"))
                .addField(support.message(guildId, "setup.panel.section.roles"),
                        support.message(guildId, "setup.panel.home.roles_value",
                                roleMappingService.getMappedRoles(guildId).size()), true)
                .addField(support.message(guildId, "setup.panel.section.event"),
                        support.message(guildId, "setup.panel.home.event_value",
                                eventSettings.bindings(guildId).size(), alerts), true)
                .addField(support.message(guildId, "setup.panel.section.leaderboards"),
                        support.message(guildId, "setup.panel.home.leaderboards_value",
                                leaderboardSetupService.count(guildId)), true)
                .addField(support.message(guildId, "setup.panel.section.general"),
                        support.message(guildId, "setup.panel.home.general_value",
                                GuildSettingsService.languageName(server.getLanguage()),
                                support.onOff(guildId, server.isAutoNickname()),
                                support.onOff(guildId, server.isUserCommandsVisible())), false);

        return new PanelView(null, List.of(embed.build()), List.of(
                ActionRow.of(
                        navButton(guildId, RolesSection.ID, "setup.panel.section.roles"),
                        navButton(guildId, EventSection.ID, "setup.panel.section.event"),
                        navButton(guildId, LeaderboardsSection.ID, "setup.panel.section.leaderboards")),
                ActionRow.of(
                        navButton(guildId, GeneralSection.ID, "setup.panel.section.general"),
                        navButton(guildId, MessagesSection.ID, "setup.panel.section.messages"),
                        Button.success(PanelAction.id(ID, "wizard"),
                                support.message(guildId, "setup.panel.section.wizard")))));
    }

    private Button navButton(String guildId, String section, String labelKey) {
        return Button.primary(PanelAction.nav(section), support.message(guildId, labelKey));
    }

    @Override
    public void onButton(ButtonInteractionEvent event, PanelAction action) {
        if ("wizard".equals(action.action())) {
            startBasicCommand.start(event);
            return;
        }
        support.show(event, render(event.getGuild()));
    }
}
