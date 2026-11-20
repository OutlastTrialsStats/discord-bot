package com.outlasttrialsstats.discordbot.feature.setup.panel;

import com.outlasttrialsstats.discordbot.shared.Texts;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

public record PanelView(String notice, List<MessageEmbed> embeds, List<ActionRow> rows) {

    public PanelView {
        embeds = List.copyOf(embeds);
        rows = List.copyOf(rows);
    }

    public PanelView withNotice(String notice) {
        return new PanelView(notice, embeds, rows);
    }

    public MessageEditData edit() {
        return new MessageEditBuilder()
                .setContent(content())
                .setEmbeds(embeds)
                .setComponents(rows)
                .setAllowedMentions(List.of())
                .build();
    }

    public MessageCreateData create() {
        return new MessageCreateBuilder()
                .setContent(content())
                .setEmbeds(embeds)
                .setComponents(rows)
                .setAllowedMentions(List.of())
                .build();
    }

    private String content() {
        if (notice == null || notice.isBlank()) {
            return "";
        }
        return Texts.truncate(notice, Message.MAX_CONTENT_LENGTH);
    }
}
