package com.outlasttrialsstats.discordbot.feature.setup.panel;

import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.feature.leaderboard.LeaderboardCategories;
import com.outlasttrialsstats.discordbot.feature.leaderboard.LeaderboardCategories.Group;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardBinding;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardSetupService;
import com.outlasttrialsstats.discordbot.shared.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.SelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LeaderboardsSection implements PanelSection {

    static final String ID = "leaderboards";
    static final int DEFAULT_PAGES = 3;
    private static final String NONE = "-";

    private final PanelSupport support;
    private final LeaderboardSetupService setupService;
    private final LeaderboardService leaderboardService;

    record Draft(Group group, StatisticType category, int pages) {

        Object[] args() {
            return new Object[]{group.name(), category == null ? NONE : category.getValue(), pages};
        }

        static Draft parse(PanelAction action) {
            Group group = PanelSupport.parseEnum(Group.class, action.arg(0)).orElse(Group.TRIALS);
            StatisticType category = LeaderboardCategories.parse(action.arg(1))
                    .filter(type -> LeaderboardCategories.groupOf(type) == group)
                    .orElse(null);
            int pages = parsePages(action.arg(2));
            return new Draft(group, category, pages);
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public PanelView render(Guild guild) {
        return render(guild, 0);
    }

    PanelView render(Guild guild, int pageIndex) {
        String guildId = guild.getId();
        List<LeaderboardBinding> bindings = setupService.list(guildId);
        PanelSupport.Page<LeaderboardBinding> page =
                PanelSupport.page(bindings, pageIndex, SelectMenu.OPTIONS_MAX_AMOUNT);

        List<String> lines = new ArrayList<>();
        lines.add(support.message(guildId, "setup.panel.leaderboards.description"));
        lines.add("");
        if (bindings.isEmpty()) {
            lines.add(support.message(guildId, "setup.panel.leaderboards.empty"));
        }
        for (LeaderboardBinding binding : bindings) {
            lines.add("• " + support.message(guildId, "setup.panel.leaderboards.entry",
                    leaderboardService.getCategoryDisplayName(guildId, binding.category()),
                    "<#" + binding.channelId() + ">", binding.maxPages()));
        }

        List<ActionRow> rows = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.primary(PanelAction.id(ID, "add"), support.message(guildId, "setup.panel.leaderboards.add")));
        buttons.addAll(support.pageButtons(page, index -> PanelAction.id(ID, "page", index)));
        rows.add(ActionRow.of(buttons));
        if (!page.items().isEmpty()) {
            StringSelectMenu.Builder remove = StringSelectMenu.create(PanelAction.id(ID, "remove", page.index()))
                    .setPlaceholder(page.label(support.message(guildId, "setup.panel.leaderboards.remove_placeholder")));
            page.items().forEach(binding -> {
                var channel = guild.getChannelById(StandardGuildMessageChannel.class, binding.channelId());
                String label = leaderboardService.getCategoryDisplayName(guildId, binding.category())
                        + (channel == null ? "" : " · #" + channel.getName());
                remove.addOption(Texts.truncate(label, SelectOption.LABEL_MAX_LENGTH), String.valueOf(binding.id()));
            });
            rows.add(ActionRow.of(remove.build()));
        }
        rows.add(support.backRow(guildId, HomeSection.ID));

        return new PanelView(null,
                List.of(support.embed(support.message(guildId, "setup.panel.section.leaderboards"))
                        .setDescription(String.join("\n", lines)).build()),
                rows);
    }

    PanelView renderAdd(Guild guild, Draft draft) {
        String guildId = guild.getId();

        StringSelectMenu.Builder groups = StringSelectMenu.create(PanelAction.id(ID, "group", draft.args()));
        for (Group group : Group.values()) {
            groups.addOption(support.message(guildId, "setup.panel.leaderboards.group." + group.name()), group.name());
        }
        groups.setDefaultValues(draft.group().name());

        StringSelectMenu.Builder categories = StringSelectMenu.create(PanelAction.id(ID, "category", draft.args()))
                .setPlaceholder(support.message(guildId, "setup.panel.leaderboards.category_placeholder"));
        for (StatisticType type : LeaderboardCategories.inGroup(draft.group())) {
            categories.addOption(Texts.truncate(leaderboardService.getCategoryDisplayName(guildId, type), SelectOption.LABEL_MAX_LENGTH),
                    type.getValue());
        }
        if (draft.category() != null) {
            categories.setDefaultValues(draft.category().getValue());
        }

        StringSelectMenu.Builder pages = StringSelectMenu.create(PanelAction.id(ID, "pages", draft.args()));
        for (int count = 1; count <= LeaderboardSetupService.MAX_PAGES; count++) {
            pages.addOption(support.message(guildId, "setup.panel.leaderboards.pages", count), String.valueOf(count));
        }
        pages.setDefaultValues(String.valueOf(draft.pages()));

        var channel = support.channelSelect(PanelAction.id(ID, "post", draft.args()),
                        support.message(guildId, "setup.panel.leaderboards.channel_placeholder"))
                .setDisabled(draft.category() == null);

        return new PanelView(null,
                List.of(support.embed(support.message(guildId, "setup.panel.leaderboards.add"))
                        .setDescription(support.message(guildId, "setup.panel.leaderboards.add_description")).build()),
                List.of(ActionRow.of(groups.build()),
                        ActionRow.of(categories.build()),
                        ActionRow.of(pages.build()),
                        ActionRow.of(channel.build()),
                        support.backRow(guildId, ID)));
    }

    @Override
    public void onButton(ButtonInteractionEvent event, PanelAction action) {
        Guild guild = event.getGuild();
        if ("add".equals(action.action())) {
            support.show(event, renderAdd(guild, new Draft(Group.TRIALS, null, DEFAULT_PAGES)));
            return;
        }
        support.show(event, render(guild, "page".equals(action.action()) ? PanelSupport.parsePage(action.arg(0)) : 0));
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, PanelAction action) {
        Guild guild = event.getGuild();
        String value = event.getValues().isEmpty() ? null : event.getValues().getFirst();
        Draft draft = Draft.parse(action);
        switch (action.action()) {
            case "group" -> support.show(event, renderAdd(guild,
                    new Draft(PanelSupport.parseEnum(Group.class, value).orElse(draft.group()), null, draft.pages())));
            case "category" -> support.show(event, renderAdd(guild,
                    new Draft(draft.group(), LeaderboardCategories.parse(value).orElse(null), draft.pages())));
            case "pages" -> support.show(event, renderAdd(guild,
                    new Draft(draft.group(), draft.category(), parsePages(value))));
            case "remove" -> {
                String notice = PanelSupport.parseId(value)
                        .map(id -> setupService.remove(guild, id)).orElse(null);
                support.show(event, render(guild, PanelSupport.parsePage(action.arg(0))).withNotice(notice));
            }
            default -> support.show(event, render(guild));
        }
    }

    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, PanelAction action) {
        Guild guild = event.getGuild();
        Draft draft = Draft.parse(action);
        Optional<StandardGuildMessageChannel> channel = support.selectedChannel(event);
        if (!"post".equals(action.action()) || draft.category() == null || channel.isEmpty()) {
            support.show(event, renderAdd(guild, draft)
                    .withNotice(support.message(guild.getId(), "error.invalid_channel_type")));
            return;
        }
        InteractionHook hook = support.defer(event);
        String notice = setupService.create(guild, channel.get(), draft.category(), draft.pages());
        support.update(hook, render(guild).withNotice(notice));
    }


    private static int parsePages(String value) {
        try {
            int pages = Integer.parseInt(value);
            return Math.max(1, Math.min(LeaderboardSetupService.MAX_PAGES, pages));
        } catch (NumberFormatException e) {
            return DEFAULT_PAGES;
        }
    }
}
