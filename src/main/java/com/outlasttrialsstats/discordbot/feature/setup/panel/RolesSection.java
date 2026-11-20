package com.outlasttrialsstats.discordbot.feature.setup.panel;

import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.RoleTarget;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingActions;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingActions.Mapping;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import com.outlasttrialsstats.discordbot.shared.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.ModalTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.SelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RolesSection implements PanelSection {

    static final String ID = "roles";
    private static final String VALUE = "value";
    private static final String ROLE = "role";

    private final PanelSupport support;
    private final RoleMappingActions actions;
    private final RoleMappingService roleMappingService;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public PanelView render(Guild guild) {
        return render(guild, null);
    }

    PanelView render(Guild guild, RoleCategory category) {
        return render(guild, category, 0);
    }

    PanelView render(Guild guild, RoleCategory category, int pageIndex) {
        String guildId = guild.getId();
        List<String> lines = new ArrayList<>();
        lines.add(support.message(guildId, "setup.panel.roles.description"));
        lines.add("");

        PanelSupport.Page<Mapping> page = PanelSupport.page(List.of(), 0, SelectMenu.OPTIONS_MAX_AMOUNT);
        if (category == null) {
            Map<RoleCategory, Integer> counts = roleMappingService.countByCategory(guildId);
            for (RoleCategory each : RoleCategory.values()) {
                lines.add(support.message(guildId, "setup.panel.roles.category_count", each.getDisplayName(),
                        counts.getOrDefault(each, 0)));
            }
        } else {
            page = PanelSupport.page(actions.list(guildId, category), pageIndex, SelectMenu.OPTIONS_MAX_AMOUNT);
            lines.add(page.label("**" + category.getDisplayName() + "**"));
            if (page.items().isEmpty()) {
                lines.add(support.message(guildId, "setup.panel.roles.empty"));
            }
            page.items().forEach(mapping -> lines.add(mappingLine(guild, mapping)));
        }

        StringSelectMenu.Builder categories = StringSelectMenu.create(PanelAction.id(ID, "category"))
                .setPlaceholder(support.message(guildId, "setup.panel.roles.category_placeholder"));
        for (RoleCategory each : RoleCategory.values()) {
            categories.addOption(each.getDisplayName(), each.name());
        }
        if (category != null) {
            categories.setDefaultValues(category.name());
        }

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(categories.build()));
        if (category != null) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.primary(PanelAction.id(ID, "add", category.name()),
                    support.message(guildId, "setup.panel.roles.add")));
            buttons.addAll(support.pageButtons(page, index -> PanelAction.id(ID, "page", category.name(), index)));
            rows.add(ActionRow.of(buttons));
            if (!page.items().isEmpty()) {
                StringSelectMenu.Builder remove = StringSelectMenu.create(
                                PanelAction.id(ID, "remove", category.name(), page.index()))
                        .setPlaceholder(page.label(support.message(guildId, "setup.panel.roles.remove_placeholder")));
                page.items().forEach(mapping ->
                        remove.addOption(Texts.truncate(optionLabel(guild, mapping), SelectOption.LABEL_MAX_LENGTH), mapping.target().key()));
                remove.setRequiredRange(1, remove.getOptions().size());
                rows.add(ActionRow.of(remove.build()));
            }
        }
        rows.add(support.backRow(guildId, HomeSection.ID));

        return new PanelView(null,
                List.of(support.embed(support.message(guildId, "setup.panel.section.roles"))
                        .setDescription(String.join("\n", lines)).build()),
                rows);
    }

    private String mappingLine(Guild guild, Mapping mapping) {
        Role role = guild.getRoleById(mapping.roleId());
        String roleText = role == null
                ? support.message(guild.getId(), "setup.panel.roles.role_missing")
                : role.getAsMention();
        return "• " + mapping.target().label() + " → " + roleText;
    }

    private String optionLabel(Guild guild, Mapping mapping) {
        Role role = guild.getRoleById(mapping.roleId());
        return role == null ? mapping.target().label() : mapping.target().label() + " → @" + role.getName();
    }

    private Modal addModal(String guildId, RoleCategory category) {
        List<ModalTopLevelComponent> components = new ArrayList<>();
        if (RoleTarget.usesThreshold(category)) {
            components.add(Label.of(support.message(guildId, "setup.panel.roles.threshold"),
                    TextInput.create(VALUE, TextInputStyle.SHORT).setRequired(true).setMaxLength(9)
                            .setPlaceholder("10").build()));
        } else {
            List<RoleTarget> choices = RoleTarget.choices(category);
            if (choices.size() > 1) {
                StringSelectMenu.Builder values = StringSelectMenu.create(VALUE);
                choices.forEach(choice -> values.addOption(choice.label(), choice.key()));
                components.add(Label.of(support.message(guildId, "setup.panel.roles.value"), values.build()));
            }
        }
        components.add(Label.of(support.message(guildId, "setup.panel.roles.role"),
                support.message(guildId, "setup.panel.roles.role_hint"),
                EntitySelectMenu.create(ROLE, EntitySelectMenu.SelectTarget.ROLE)
                        .setRequired(false).setRequiredRange(0, 1).build()));
        return Modal.create(PanelAction.id(ID, "add", category.name()),
                        Texts.truncate(support.message(guildId, "setup.panel.roles.add_title",
                                category.getDisplayName()), Modal.MAX_TITLE_LENGTH))
                .addComponents(components)
                .build();
    }

    @Override
    public void onButton(ButtonInteractionEvent event, PanelAction action) {
        Optional<RoleCategory> category = PanelSupport.parseEnum(RoleCategory.class, action.arg(0));
        if ("add".equals(action.action()) && category.isPresent()) {
            event.replyModal(addModal(event.getGuild().getId(), category.get())).queue();
            return;
        }
        support.show(event, render(event.getGuild(), category.orElse(null),
                "page".equals(action.action()) ? PanelSupport.parsePage(action.arg(1)) : 0));
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, PanelAction action) {
        Guild guild = event.getGuild();
        switch (action.action()) {
            case "category" -> support.show(event, render(guild,
                    event.getValues().isEmpty() ? null : PanelSupport.parseEnum(RoleCategory.class, event.getValues().getFirst()).orElse(null)));
            case "remove" -> {
                Optional<RoleCategory> category = PanelSupport.parseEnum(RoleCategory.class, action.arg(0));
                if (category.isEmpty()) {
                    support.show(event, render(guild));
                    return;
                }
                List<String> notices = new ArrayList<>();
                for (String key : event.getValues()) {
                    RoleTarget.parse(category.get(), key)
                            .ifPresent(target -> notices.add(actions.remove(guild, target)));
                }
                support.show(event, render(guild, category.get(), PanelSupport.parsePage(action.arg(1)))
                        .withNotice(String.join("\n", notices)));
            }
            default -> support.show(event, render(guild));
        }
    }

    @Override
    public void onModal(ModalInteractionEvent event, PanelAction action) {
        Guild guild = event.getGuild();
        Optional<RoleCategory> category = PanelSupport.parseEnum(RoleCategory.class, action.arg(0));
        if (category.isEmpty()) {
            support.show(event, render(guild));
            return;
        }
        Optional<RoleTarget> target = target(event, category.get());
        if (target.isEmpty()) {
            support.show(event, render(guild, category.get())
                    .withNotice(support.message(guild.getId(), "setup.panel.roles.invalid_threshold")));
            return;
        }
        ModalMapping roleMapping = event.getValue(ROLE);
        List<Role> roles = roleMapping == null ? List.of() : roleMapping.getAsMentions().getRoles();
        Role role = roles.isEmpty() ? null : roles.getFirst();

        InteractionHook hook = support.defer(event);
        actions.add(guild, event.getMember(), target.get(), role,
                notice -> support.update(hook, render(guild, category.get()).withNotice(notice)));
    }

    private static Optional<RoleTarget> target(ModalInteractionEvent event, RoleCategory category) {
        ModalMapping value = event.getValue(VALUE);
        if (value == null) {
            List<RoleTarget> choices = RoleTarget.choices(category);
            return choices.size() == 1 ? Optional.of(choices.getFirst()) : Optional.empty();
        }
        String key = RoleTarget.usesThreshold(category)
                ? value.getAsString()
                : value.getAsStringList().stream().findFirst().orElse(null);
        return RoleTarget.parse(category, key);
    }

}
