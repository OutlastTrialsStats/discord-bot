package com.outlasttrialsstats.discordbot.feature.setup.panel;

import com.outlasttrialsstats.discordbot.feature.setup.service.MessageFileService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessagesSection implements PanelSection {

    static final String ID = "messages";
    private static final String FILE = "file";

    private final PanelSupport support;
    private final MessageFileService messageFileService;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public PanelView render(Guild guild) {
        String guildId = guild.getId();
        return new PanelView(null,
                List.of(support.embed(support.message(guildId, "setup.panel.section.messages"))
                        .setDescription(support.message(guildId, "setup.panel.messages.description")).build()),
                List.of(ActionRow.of(
                                Button.secondary(PanelAction.id(ID, "download"),
                                        support.message(guildId, "setup.panel.messages.download")),
                                Button.primary(PanelAction.id(ID, "upload"),
                                        support.message(guildId, "setup.panel.messages.upload")),
                                Button.danger(PanelAction.id(ID, "reset"),
                                        support.message(guildId, "setup.panel.messages.reset"))),
                        support.backRow(guildId, HomeSection.ID)));
    }

    @Override
    public void onButton(ButtonInteractionEvent event, PanelAction action) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        switch (action.action()) {
            case "download" -> event.replyFiles(messageFileService.exportFile(guildId)).setEphemeral(true).queue();
            case "upload" -> event.replyModal(Modal.create(PanelAction.id(ID, "upload"),
                            support.message(guildId, "setup.panel.messages.upload"))
                    .addComponents(Label.of(support.message(guildId, "setup.panel.messages.upload_label"),
                            AttachmentUpload.create(FILE).setRequiredRange(1, 1).build()))
                    .build()).queue();
            case "reset" -> support.show(event, support.confirm(guildId, ID, "setup.panel.messages.reset",
                    "setup.panel.messages.reset_confirm", "reset-confirm"));
            case "reset-confirm" -> support.show(event, render(guild).withNotice(messageFileService.reset(guildId)));
            default -> support.show(event, render(guild));
        }
    }

    @Override
    public void onModal(ModalInteractionEvent event, PanelAction action) {
        Guild guild = event.getGuild();
        ModalMapping mapping = event.getValue(FILE);
        List<Attachment> files = mapping == null ? List.of() : mapping.getAsAttachmentList();
        if (files.isEmpty()) {
            support.show(event, render(guild).withNotice(
                    support.message(guild.getId(), "setup.messages.invalid_file")));
            return;
        }
        InteractionHook hook = support.defer(event);
        messageFileService.importFile(guild.getId(), files.getFirst(),
                notice -> support.update(hook, render(guild).withNotice(notice)));
    }
}
