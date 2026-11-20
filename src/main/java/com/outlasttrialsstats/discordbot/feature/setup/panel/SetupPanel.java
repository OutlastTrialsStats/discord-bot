package com.outlasttrialsstats.discordbot.feature.setup.panel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import org.springframework.stereotype.Component;

@Component
public class SetupPanel {

    private final Map<String, PanelSection> sections;

    public SetupPanel(List<PanelSection> sections) {
        this.sections = sections.stream().collect(Collectors.toMap(PanelSection::id, Function.identity()));
    }

    public void open(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            return;
        }
        event.reply(sections.get(HomeSection.ID).render(event.getGuild()).create()).setEphemeral(true).queue();
    }

    public void onButton(ButtonInteractionEvent event) {
        resolve(event, event.getComponentId())
                .ifPresent(target -> target.section().onButton(event, target.action()));
    }

    public void onStringSelect(StringSelectInteractionEvent event) {
        resolve(event, event.getComponentId())
                .ifPresent(target -> target.section().onStringSelect(event, target.action()));
    }

    public void onEntitySelect(EntitySelectInteractionEvent event) {
        resolve(event, event.getComponentId())
                .ifPresent(target -> target.section().onEntitySelect(event, target.action()));
    }

    public void onModal(ModalInteractionEvent event) {
        resolve(event, event.getModalId())
                .ifPresent(target -> target.section().onModal(event, target.action()));
    }

    private record Target(PanelSection section, PanelAction action) {
    }

    private Optional<Target> resolve(Interaction event, String componentId) {
        if (event.getGuild() == null) {
            return Optional.empty();
        }
        return PanelAction.parse(componentId).map(action ->
                new Target(sections.getOrDefault(action.section(), sections.get(HomeSection.ID)), action));
    }
}
