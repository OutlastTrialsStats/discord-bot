package com.outlasttrialsstats.discordbot.feature.profile.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.feature.profile.dto.RoleAssignmentResult;
import com.outlasttrialsstats.discordbot.feature.profile.service.RoleAssignmentService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import com.outlasttrialsstats.discordbot.shared.SiteLinks;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileCommandTest {

    private static final String GUILD_ID = "guild-1";

    @Mock
    private RoleAssignmentService roleAssignmentService;

    @Mock
    private MessageService messageService;

    @Mock
    private SlashCommandInteractionEvent event;

    @Mock
    private Guild guild;

    @Mock
    private SelfMember selfMember;

    @Mock
    private Member member;

    @Mock
    private InteractionHook hook;

    private ProfileCommand command;

    @BeforeEach
    void setUp() {
        command = new ProfileCommand(roleAssignmentService, messageService);

        var jda = mock(JDA.class);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn(GUILD_ID);
        when(event.getJDA()).thenReturn(jda);
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
        when(guild.getSelfMember()).thenReturn(selfMember);
        when(event.deferReply(true)).thenReturn(mock(ReplyCallbackAction.class));
        when(event.getHook()).thenReturn(hook);
        lenient().when(hook.editOriginal(anyString())).thenReturn(mock(WebhookMessageEditAction.class));
        lenient().when(event.getMember()).thenReturn(member);
        lenient().when(messageService.getMessage(eq(GUILD_ID), anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(1));
    }

    @Test
    void onProfileUpdate_defersBeforeBackendLookup() {
        when(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        when(roleAssignmentService.assignRoles(guild, member))
                .thenReturn(RoleAssignmentResult.of(List.of("A"), List.of()));

        command.onProfileUpdate(event);

        var order = inOrder(event, roleAssignmentService, hook);
        order.verify(event).deferReply(true);
        order.verify(roleAssignmentService).assignRoles(guild, member);
        order.verify(hook).editOriginal("profile.updated");
        verify(event, never()).reply(anyString());
    }

    @Test
    void onProfileUpdate_missingPermission_editsDeferredReply() {
        when(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(false);

        command.onProfileUpdate(event);

        verify(event).deferReply(true);
        verify(hook).editOriginal("error.missing_permission.manage_roles");
        verify(roleAssignmentService, never()).assignRoles(any(), any());
    }

    @Test
    void onProfileUpdate_lookupFailed_editsDeferredReply() {
        when(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        when(roleAssignmentService.assignRoles(guild, member)).thenReturn(RoleAssignmentResult.lookupFailed());

        command.onProfileUpdate(event);

        verify(hook).editOriginal("profile.backend_unavailable");
    }

    @Test
    @SuppressWarnings("unchecked")
    void onProfileUpdate_notVerified_showsOnboardingWithLinkButton() {
        when(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        when(roleAssignmentService.assignRoles(guild, member)).thenReturn(RoleAssignmentResult.notVerified());
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class, RETURNS_SELF);
        when(hook.editOriginalEmbeds(any(MessageEmbed.class))).thenReturn(edit);

        command.onProfileUpdate(event);

        ArgumentCaptor<MessageEmbed> embed = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(hook).editOriginalEmbeds(embed.capture());
        assertThat(embed.getValue().getTitle()).isEqualTo("profile.onboarding.title");
        assertThat(embed.getValue().getDescription()).isEqualTo("profile.onboarding.description");

        ArgumentCaptor<ActionRow> row = ArgumentCaptor.forClass(ActionRow.class);
        verify(edit).setComponents(row.capture());
        Button button = row.getValue().getButtons().getFirst();
        assertThat(button.getUrl()).isEqualTo(SiteLinks.LINK_URL);
        assertThat(button.getLabel()).isEqualTo("profile.onboarding.button");
        verify(hook, never()).editOriginal(anyString());
    }

    @Test
    void onProfileUpdate_noChanges_editsDeferredReply() {
        when(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        when(roleAssignmentService.assignRoles(guild, member))
                .thenReturn(RoleAssignmentResult.of(List.of(), List.of()));

        command.onProfileUpdate(event);

        verify(hook).editOriginal("profile.no_changes");
    }
}
