package com.outlasttrialsstats.discordbot.feature.setup.panel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SetupPanelTest {

    private final PanelSection home = section(HomeSection.ID);
    private final PanelSection roles = section(RolesSection.ID);

    private SetupPanel panel;

    @BeforeEach
    void setUp() {
        panel = new SetupPanel(List.of(home, roles));
    }

    @Test
    void navButton_isRoutedToTargetSection() {
        ButtonInteractionEvent event = button(PanelAction.nav(RolesSection.ID));

        panel.onButton(event);

        verify(roles).onButton(event, new PanelAction(RolesSection.ID, PanelAction.VIEW, List.of()));
        verify(home, never()).onButton(any(), any());
    }

    @Test
    void actionButton_isHandledBySection() {
        ButtonInteractionEvent event = button(PanelAction.id(RolesSection.ID, "add", "PRESTIGE"));

        panel.onButton(event);

        verify(roles).onButton(eq(event), eq(new PanelAction(RolesSection.ID, "add", List.of("PRESTIGE"))));
    }

    @Test
    void unknownSection_fallsBackToHome() {
        ButtonInteractionEvent event = button(PanelAction.nav("gone"));

        panel.onButton(event);

        verify(home).onButton(event, new PanelAction("gone", PanelAction.VIEW, List.of()));
    }

    @Test
    void select_isRoutedWithoutCheckingMemberPermissions() {
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getComponentId()).thenReturn(PanelAction.id(RolesSection.ID, "category"));

        panel.onStringSelect(event);

        verify(roles).onStringSelect(event, new PanelAction(RolesSection.ID, "category", List.of()));
        verify(event, never()).getMember();
    }

    @Test
    void interactionOutsideAGuild_isIgnored() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn(PanelAction.nav(RolesSection.ID));
        when(event.getGuild()).thenReturn(null);

        panel.onButton(event);

        verify(roles, never()).onButton(any(), any());
    }

    private static PanelSection section(String id) {
        PanelSection section = mock(PanelSection.class);
        when(section.id()).thenReturn(id);
        return section;
    }

    private static ButtonInteractionEvent button(String componentId) {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getComponentId()).thenReturn(componentId);
        Guild guild = event.getGuild();
        when(guild.getId()).thenReturn("guild-1");
        return event;
    }
}
