package com.outlasttrialsstats.discordbot.feature.setup.panel;

import com.outlasttrialsstats.discordbot.shared.BotColors;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PanelSupport {

    private final MessageService messageService;

    public String message(String guildId, String key, Object... args) {
        return messageService.getMessage(guildId, key, args);
    }

    public EmbedBuilder embed(String title) {
        return new EmbedBuilder().setColor(BotColors.BRAND).setTitle(title);
    }

    public ActionRow backRow(String guildId, String section) {
        return ActionRow.of(backButton(guildId, section));
    }

    public Button backButton(String guildId, String section) {
        return Button.secondary(PanelAction.nav(section), "← " + message(guildId, "setup.panel.back"));
    }

    public PanelView confirm(String guildId, String section, String titleKey, String descriptionKey,
                             String confirmAction) {
        return new PanelView(null,
                List.of(embed(message(guildId, titleKey)).setDescription(message(guildId, descriptionKey)).build()),
                List.of(ActionRow.of(
                        Button.danger(PanelAction.id(section, confirmAction), message(guildId, "setup.panel.confirm")),
                        Button.secondary(PanelAction.nav(section), message(guildId, "setup.panel.cancel")))));
    }

    public String onOff(String guildId, boolean value) {
        return message(guildId, value ? "setup.panel.on" : "setup.panel.off");
    }

    public EntitySelectMenu.Builder channelSelect(String customId, String placeholder) {
        return EntitySelectMenu.create(customId, EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)
                .setPlaceholder(placeholder);
    }

    public Optional<StandardGuildMessageChannel> selectedChannel(EntitySelectInteractionEvent event) {
        List<GuildChannel> channels = event.getMentions().getChannels();
        if (channels.isEmpty() || !(channels.getFirst() instanceof StandardGuildMessageChannel channel)) {
            return Optional.empty();
        }
        return Optional.of(channel);
    }

    public void show(IMessageEditCallback event, PanelView view) {
        event.editMessage(view.edit()).queue();
    }

    public InteractionHook defer(IMessageEditCallback event) {
        event.deferEdit().queue();
        return event.getHook();
    }

    public void update(InteractionHook hook, PanelView view) {
        hook.editOriginal(view.edit()).queue();
    }

    public record Page<T>(List<T> items, int index, int count) {

        public String label(String text) {
            return count > 1 ? text + " (" + (index + 1) + "/" + count + ")" : text;
        }
    }

    public static <T> Page<T> page(List<T> all, int index, int size) {
        int count = Math.max(1, (all.size() + size - 1) / size);
        int current = Math.max(0, Math.min(index, count - 1));
        return new Page<>(all.subList(current * size, Math.min(all.size(), (current + 1) * size)), current, count);
    }

    public List<Button> pageButtons(Page<?> page, IntFunction<String> idForPage) {
        if (page.count() <= 1) {
            return List.of();
        }
        return List.of(
                Button.secondary(idForPage.apply(page.index() - 1), "◀").withDisabled(page.index() == 0),
                Button.secondary(idForPage.apply(page.index() + 1), "▶")
                        .withDisabled(page.index() == page.count() - 1));
    }

    public static int parsePage(String value) {
        return parseId(value).map(Long::intValue).orElse(0);
    }

    public static <E extends Enum<E>> Optional<E> parseEnum(Class<E> type, String name) {
        if (name == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(type, name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<Long> parseId(String value) {
        if (value == null || value.isEmpty() || !value.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
