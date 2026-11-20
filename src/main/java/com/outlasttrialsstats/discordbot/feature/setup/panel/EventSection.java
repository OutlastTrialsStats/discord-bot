package com.outlasttrialsstats.discordbot.feature.setup.panel;

import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import com.outlasttrialsstats.discordbot.entity.CommunityAlertType;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.AlertRule;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventBinding;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventSettings;
import com.outlasttrialsstats.discordbot.shared.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.SelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventSection implements PanelSection {

    static final String ID = "event";
    static final String CURRENT = "current";

    private final PanelSupport support;
    private final CommunityEventSettings settings;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public PanelView render(Guild guild) {
        String guildId = guild.getId();
        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
                Button.primary(PanelAction.id(ID, "post-view"), support.message(guildId, "setup.panel.event.post")),
                Button.primary(PanelAction.id(ID, "alerts-view"),
                        support.message(guildId, "setup.panel.event.alerts"))));

        List<CommunityEventBinding> bindings = settings.bindings(guildId);
        if (!bindings.isEmpty()) {
            StringSelectMenu.Builder remove = StringSelectMenu.create(PanelAction.id(ID, "remove"))
                    .setPlaceholder(support.message(guildId, "setup.panel.event.remove_placeholder"));
            bindings.stream().limit(SelectMenu.OPTIONS_MAX_AMOUNT).forEach(binding -> remove.addOption(
                    Texts.truncate(settings.bindingLabel(guild, binding), SelectOption.LABEL_MAX_LENGTH), String.valueOf(binding.id())));
            rows.add(ActionRow.of(remove.build()));
        }
        rows.add(support.backRow(guildId, HomeSection.ID));

        return new PanelView(null, List.of(settings.statusEmbed(guild)), rows);
    }

    PanelView renderPost(Guild guild, String target) {
        return renderPost(guild, target, 0);
    }

    PanelView renderPost(Guild guild, String target, int pageIndex) {
        String guildId = guild.getId();
        List<DiscordCommunityEvent> events = settings.knownEvents();
        PanelSupport.Page<DiscordCommunityEvent> page =
                PanelSupport.page(events, pageIndex, SelectMenu.OPTIONS_MAX_AMOUNT - 1);

        StringSelectMenu.Builder targets = StringSelectMenu.create(PanelAction.id(ID, "target", page.index()))
                .setPlaceholder(page.label(support.message(guildId, "setup.panel.event.target_placeholder")))
                .addOption(support.message(guildId, "setup.panel.event.current"), CURRENT);
        page.items().forEach(event -> targets.addOption(
                Texts.truncate(event.getName(), SelectOption.LABEL_MAX_LENGTH), event.getId(),
                Texts.truncate(event.getId(), SelectOption.DESCRIPTION_MAX_LENGTH)));
        String selected = events.stream().anyMatch(event -> event.getId().equals(target)) ? target : CURRENT;
        if (CURRENT.equals(selected) || page.items().stream().anyMatch(event -> event.getId().equals(selected))) {
            targets.setDefaultValues(selected);
        }

        String selectedName = CURRENT.equals(selected)
                ? support.message(guildId, "setup.panel.event.current")
                : events.stream().filter(event -> event.getId().equals(selected))
                        .map(DiscordCommunityEvent::getName).findFirst().orElse(selected);

        String description = support.message(guildId, "setup.panel.event.post_description") + "\n\n"
                + support.message(guildId, "setup.panel.event.post_selected", selectedName);

        return new PanelView(null,
                List.of(support.embed(support.message(guildId, "setup.panel.event.post"))
                        .setDescription(description).build()),
                List.of(ActionRow.of(targets.build()),
                        ActionRow.of(support.channelSelect(PanelAction.id(ID, "post", selected),
                                support.message(guildId, "setup.panel.event.channel_placeholder")).build()),
                        ActionRow.of(navButtons(guildId, page, selected))));
    }

    private List<Button> navButtons(String guildId, PanelSupport.Page<?> page, String selected) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(support.backButton(guildId, ID));
        buttons.addAll(support.pageButtons(page, index -> PanelAction.id(ID, "post-page", index, selected)));
        return buttons;
    }

    PanelView renderAlerts(Guild guild) {
        String guildId = guild.getId();
        Optional<String> alertChannelId = settings.alertChannelId(guildId);
        Map<CommunityAlertType, AlertRule> rules = settings.rules(guildId);

        EntitySelectMenu.Builder channel = support.channelSelect(PanelAction.id(ID, "alert-channel"),
                support.message(guildId, "setup.panel.event.alert_channel_placeholder"));
        alertChannelId
                .filter(id -> guild.getChannelById(StandardGuildMessageChannel.class, id) != null)
                .ifPresent(id -> channel.setDefaultValues(EntitySelectMenu.DefaultValue.channel(id)));

        List<Button> buttons = new ArrayList<>();
        for (CommunityAlertType type : CommunityAlertType.values()) {
            boolean enabled = rules.get(type).enabled();
            String label = support.message(guildId, "setup.panel.event.toggle", settings.typeName(guildId, type),
                    support.onOff(guildId, enabled));
            String id = PanelAction.id(ID, "toggle", type.name());
            buttons.add(enabled ? Button.success(id, label) : Button.secondary(id, label));
        }
        buttons.add(Button.danger(PanelAction.id(ID, "alerts-off"),
                support.message(guildId, "setup.panel.event.alerts_off")).withDisabled(alertChannelId.isEmpty()));
        buttons.add(support.backButton(guildId, ID));

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(channel.build()));
        rows.add(ActionRow.of(buttons));
        for (CommunityAlertType type : CommunityAlertType.values()) {
            EntitySelectMenu.Builder role = EntitySelectMenu.create(PanelAction.id(ID, "role", type.name()),
                            EntitySelectMenu.SelectTarget.ROLE)
                    .setPlaceholder(Texts.truncate(support.message(guildId, "setup.panel.event.role_placeholder",
                            settings.typeName(guildId, type)), SelectMenu.PLACEHOLDER_MAX_LENGTH))
                    .setRequiredRange(0, 1);
            String roleId = rules.get(type).roleId();
            if (roleId != null && guild.getRoleById(roleId) != null) {
                role.setDefaultValues(EntitySelectMenu.DefaultValue.role(roleId));
            }
            rows.add(ActionRow.of(role.build()));
        }
        for (CommunityAlertType type : CommunityAlertType.values()) {
            if (!type.hasLeadTime()) {
                continue;
            }
            StringSelectMenu.Builder leadTime = StringSelectMenu.create(PanelAction.id(ID, "lead-time", type.name()));
            for (int minutes : CommunityEventSettings.LEAD_TIMES) {
                leadTime.addOption(support.message(guildId, "setup.panel.event.lead_time",
                        settings.typeName(guildId, type), CommunityEventSettings.formatLeadTime(minutes)),
                        String.valueOf(minutes));
            }
            Integer current = rules.get(type).leadTimeMinutes();
            if (current != null) {
                leadTime.setDefaultValues(String.valueOf(current));
            }
            rows.add(ActionRow.of(leadTime.build()));
        }

        return new PanelView(null, List.of(settings.statusEmbed(guild)), rows);
    }

    @Override
    public void onButton(ButtonInteractionEvent event, PanelAction action) {
        Guild guild = event.getGuild();
        switch (action.action()) {
            case "post-view" -> support.show(event, renderPost(guild, CURRENT));
            case "post-page" -> support.show(event, renderPost(guild,
                    String.join(":", action.args().subList(Math.min(1, action.args().size()), action.args().size())),
                    PanelSupport.parsePage(action.arg(0))));
            case "alerts-view" -> support.show(event, renderAlerts(guild));
            case "alerts-off" -> support.show(event, renderAlerts(guild).withNotice(settings.disableAlerts(guild)));
            case "toggle" -> {
                Optional<CommunityAlertType> type = PanelSupport.parseEnum(CommunityAlertType.class, action.arg(0));
                if (type.isEmpty()) {
                    support.show(event, renderAlerts(guild));
                    return;
                }
                boolean enabled = !settings.rules(guild.getId()).get(type.get()).enabled();
                String notice = settings.setEnabled(guild, type.get(), enabled);
                support.show(event, renderAlerts(guild).withNotice(notice));
            }
            default -> support.show(event, render(guild));
        }
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, PanelAction action) {
        Guild guild = event.getGuild();
        String value = event.getValues().isEmpty() ? null : event.getValues().getFirst();
        switch (action.action()) {
            case "target" -> support.show(event, renderPost(guild, value, PanelSupport.parsePage(action.arg(0))));
            case "remove" -> {
                String notice = PanelSupport.parseId(value)
                        .map(id -> settings.removeMessage(guild, id)).orElse(null);
                support.show(event, render(guild).withNotice(notice));
            }
            case "lead-time" -> {
                Optional<CommunityAlertType> type = PanelSupport.parseEnum(CommunityAlertType.class, action.arg(0));
                if (type.isEmpty() || value == null) {
                    support.show(event, renderAlerts(guild));
                    return;
                }
                String notice = settings.setLeadTime(guild, type.get(), Integer.parseInt(value));
                support.show(event, renderAlerts(guild).withNotice(notice));
            }
            default -> support.show(event, render(guild));
        }
    }

    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, PanelAction action) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        switch (action.action()) {
            case "post" -> {
                String target = String.join(":", action.args());
                Optional<StandardGuildMessageChannel> channel = support.selectedChannel(event);
                if (channel.isEmpty()) {
                    support.show(event, renderPost(guild, target)
                            .withNotice(support.message(guildId, "error.invalid_channel_type")));
                    return;
                }
                String eventId = target.isEmpty() || CURRENT.equals(target) ? null : target;
                InteractionHook hook = support.defer(event);
                support.update(hook, render(guild).withNotice(settings.postMessage(guild, channel.get(), eventId)));
            }
            case "alert-channel" -> {
                Optional<StandardGuildMessageChannel> channel = support.selectedChannel(event);
                String notice = channel.isEmpty()
                        ? support.message(guildId, "error.invalid_channel_type")
                        : settings.setAlertChannel(guild, channel.get());
                support.show(event, renderAlerts(guild).withNotice(notice));
            }
            case "role" -> {
                Optional<CommunityAlertType> type = PanelSupport.parseEnum(CommunityAlertType.class, action.arg(0));
                if (type.isEmpty()) {
                    support.show(event, renderAlerts(guild));
                    return;
                }
                List<Role> roles = event.getMentions().getRoles();
                String notice = settings.setRole(guild, type.get(), roles.isEmpty() ? null : roles.getFirst());
                support.show(event, renderAlerts(guild).withNotice(notice));
            }
            default -> support.show(event, render(guild));
        }
    }

}
