package com.outlasttrialsstats.discordbot.feature.setup.panel;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

public interface PanelSection {

    String id();

    PanelView render(Guild guild);

    default void onButton(ButtonInteractionEvent event, PanelAction action) {
    }

    default void onStringSelect(StringSelectInteractionEvent event, PanelAction action) {
    }

    default void onEntitySelect(EntitySelectInteractionEvent event, PanelAction action) {
    }

    default void onModal(ModalInteractionEvent event, PanelAction action) {
    }
}
