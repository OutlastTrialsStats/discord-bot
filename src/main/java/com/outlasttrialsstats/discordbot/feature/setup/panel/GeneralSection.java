package com.outlasttrialsstats.discordbot.feature.setup.panel;

import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.feature.profile.command.SyncAllCommand;
import com.outlasttrialsstats.discordbot.feature.setup.service.GuildSettingsService;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleCleanupService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GeneralSection implements PanelSection {

    static final String ID = "general";

    private final PanelSupport support;
    private final GuildSettingsService settingsService;
    private final SyncAllCommand syncAllCommand;
    private final RoleCleanupService roleCleanupService;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public PanelView render(Guild guild) {
        String guildId = guild.getId();
        GuildServer server = settingsService.get(guildId);

        String description = String.join("\n\n",
                support.message(guildId, "setup.panel.general.language",
                        GuildSettingsService.languageName(server.getLanguage())),
                support.message(guildId, "setup.panel.general.nickname",
                        support.onOff(guildId, server.isAutoNickname())),
                support.message(guildId, "setup.panel.general.visibility",
                        support.onOff(guildId, server.isUserCommandsVisible())));

        StringSelectMenu.Builder language = StringSelectMenu.create(PanelAction.id(ID, "language"));
        for (String code : GuildSettingsService.LANGUAGES) {
            language.addOption(GuildSettingsService.languageName(code), code);
        }
        language.setDefaultValues(server.getLanguage());

        return new PanelView(null,
                List.of(support.embed(support.message(guildId, "setup.panel.section.general"))
                        .setDescription(description).build()),
                List.of(ActionRow.of(language.build()),
                        ActionRow.of(
                                Button.secondary(PanelAction.id(ID, "nickname"), support.message(guildId,
                                        server.isAutoNickname() ? "setup.panel.general.nickname_off"
                                                : "setup.panel.general.nickname_on")),
                                Button.secondary(PanelAction.id(ID, "visibility"), support.message(guildId,
                                        server.isUserCommandsVisible() ? "setup.panel.general.visibility_off"
                                                : "setup.panel.general.visibility_on"))),
                        ActionRow.of(
                                Button.primary(PanelAction.id(ID, "sync"),
                                        support.message(guildId, "setup.panel.general.sync")),
                                Button.danger(PanelAction.id(ID, "delete"),
                                        support.message(guildId, "setup.panel.general.delete"))),
                        support.backRow(guildId, HomeSection.ID)));
    }

    @Override
    public void onButton(ButtonInteractionEvent event, PanelAction action) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        switch (action.action()) {
            case "nickname" -> {
                boolean enabled = !settingsService.get(guildId).isAutoNickname();
                support.show(event, render(guild).withNotice(settingsService.setAutoNickname(guild, enabled)));
            }
            case "visibility" -> {
                boolean visible = !settingsService.get(guildId).isUserCommandsVisible();
                support.show(event, render(guild).withNotice(settingsService.setUserCommandsVisible(guildId, visible)));
            }
            case "sync" -> {
                InteractionHook hook = support.defer(event);
                syncAllCommand.start(guild, notice -> support.update(hook, render(guild).withNotice(notice)));
            }
            case "delete" -> support.show(event, support.confirm(guildId, ID, "setup.panel.general.delete",
                    "setup.panel.general.delete_confirm", "delete-confirm"));
            case "delete-confirm" -> {
                InteractionHook hook = support.defer(event);
                roleCleanupService.deleteAll(guild, notice -> support.update(hook, render(guild).withNotice(notice)));
            }
            default -> support.show(event, render(guild));
        }
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, PanelAction action) {
        Guild guild = event.getGuild();
        if ("language".equals(action.action()) && !event.getValues().isEmpty()) {
            String notice = settingsService.setLanguage(guild.getId(), event.getValues().getFirst());
            support.show(event, render(guild).withNotice(notice));
            return;
        }
        support.show(event, render(guild));
    }
}
