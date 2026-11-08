package com.outlasttrialsstats.discordbot.feature.setup.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService.MappedRole;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.List;
import java.util.function.Consumer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteCommandTest {

    private static final String GUILD_ID = "guild-1";

    @Mock
    private RoleMappingService roleMappingService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private DeleteCommand deleteCommand;

    @Mock
    private SlashCommandInteractionEvent event;

    @Mock
    private Guild guild;

    @Mock
    private SelfMember selfMember;

    @Mock
    private InteractionHook hook;

    @Mock
    private WebhookMessageEditAction<Message> editAction;

    @BeforeEach
    void setUp() {
        when(guild.getId()).thenReturn(GUILD_ID);
        when(event.getGuild()).thenReturn(guild);
        JDA jda = mock(JDA.class);
        when(event.getJDA()).thenReturn(jda);
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
        when(guild.getSelfMember()).thenReturn(selfMember);
        when(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        lenient().when(event.deferReply(true)).thenReturn(mock(ReplyCallbackAction.class));
        lenient().when(event.getHook()).thenReturn(hook);
        lenient().when(hook.editOriginal(anyString())).thenReturn(editAction);
        lenient().when(messageService.getMessage(any(), anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(1) + ":" + joinArgs(inv.getRawArguments()));
    }

    @Test
    void botCreatedRole_isDeletedAndMappingRemoved() {
        Role role = deletableRole("role-1");
        succeedDelete(role);
        when(roleMappingService.getMappedRoles(GUILD_ID)).thenReturn(List.of(new MappedRole("role-1", true)));

        deleteCommand.onDelete(event);

        verify(role).delete();
        verify(roleMappingService).deleteMappingsByRoleIds(GUILD_ID, List.of("role-1"));
        verify(hook).editOriginal("setup.delete.completed:1,0,0");
    }

    @Test
    void severalRoles_mappingsRemovedInOneCallBeforeReply() {
        Role role = deletableRole("role-1");
        succeedDelete(role);
        when(guild.getRoleById("role-2")).thenReturn(null);
        when(roleMappingService.getMappedRoles(GUILD_ID)).thenReturn(List.of(
                new MappedRole("role-1", true), new MappedRole("role-2", false)));

        deleteCommand.onDelete(event);

        InOrder inOrder = inOrder(roleMappingService, hook);
        inOrder.verify(roleMappingService).deleteMappingsByRoleIds(eq(GUILD_ID),
                argThat(ids -> ids.size() == 2 && ids.containsAll(List.of("role-1", "role-2"))));
        inOrder.verify(hook).editOriginal("setup.delete.completed:1,1,0");
        verify(hook, times(1)).editOriginal(anyString());
    }

    @Test
    void linkedRole_isOnlyUnlinkedNotDeleted() {
        Role role = mock(Role.class);
        when(guild.getRoleById("role-1")).thenReturn(role);
        when(roleMappingService.getMappedRoles(GUILD_ID)).thenReturn(List.of(new MappedRole("role-1", false)));

        deleteCommand.onDelete(event);

        verify(role, never()).delete();
        verify(roleMappingService).deleteMappingsByRoleIds(GUILD_ID, List.of("role-1"));
        verify(hook).editOriginal("setup.delete.completed:0,1,0");
    }

    @Test
    void roleAlreadyGone_mappingRemovedAsUnlinked() {
        when(guild.getRoleById("role-1")).thenReturn(null);
        when(roleMappingService.getMappedRoles(GUILD_ID)).thenReturn(List.of(new MappedRole("role-1", true)));

        deleteCommand.onDelete(event);

        verify(roleMappingService).deleteMappingsByRoleIds(GUILD_ID, List.of("role-1"));
        verify(hook).editOriginal("setup.delete.completed:0,1,0");
    }

    @Test
    void botCreatedRoleAboveBot_isSkippedAndMappingKept() {
        Role role = mock(Role.class);
        when(guild.getRoleById("role-1")).thenReturn(role);
        when(selfMember.canInteract(role)).thenReturn(false);
        when(roleMappingService.getMappedRoles(GUILD_ID)).thenReturn(List.of(new MappedRole("role-1", true)));

        deleteCommand.onDelete(event);

        verify(role, never()).delete();
        verify(roleMappingService, never()).deleteMappingsByRoleIds(any(), any());
        verify(hook).editOriginal("setup.delete.completed:0,0,1");
    }

    @Test
    void managedRole_isSkipped() {
        Role role = mock(Role.class);
        when(guild.getRoleById("role-1")).thenReturn(role);
        when(role.isManaged()).thenReturn(true);
        when(roleMappingService.getMappedRoles(GUILD_ID)).thenReturn(List.of(new MappedRole("role-1", true)));

        deleteCommand.onDelete(event);

        verify(role, never()).delete();
        verify(hook).editOriginal("setup.delete.completed:0,0,1");
    }

    @Test
    void deleteFailsAsynchronously_mappingKeptAndCounterCompletes() {
        Role role = deletableRole("role-1");
        AuditableRestAction<Void> action = mockDeleteAction(role);
        doAnswer(inv -> {
            Consumer<Throwable> failure = inv.getArgument(1);
            failure.accept(new RuntimeException("Missing Permissions"));
            return null;
        }).when(action).queue(any(), any());
        when(roleMappingService.getMappedRoles(GUILD_ID)).thenReturn(List.of(new MappedRole("role-1", true)));

        deleteCommand.onDelete(event);

        verify(roleMappingService, never()).deleteMappingsByRoleIds(any(), any());
        verify(hook).editOriginal("setup.delete.completed:0,0,1");
    }

    @Test
    void deleteThrowsSynchronously_counterStillCompletes() {
        Role failing = deletableRole("role-1");
        when(failing.delete()).thenThrow(new HierarchyException("Can't modify a role with higher or equal highest role"));
        Role ok = deletableRole("role-2");
        succeedDelete(ok);
        when(roleMappingService.getMappedRoles(GUILD_ID)).thenReturn(List.of(
                new MappedRole("role-1", true), new MappedRole("role-2", true)));

        deleteCommand.onDelete(event);

        verify(roleMappingService).deleteMappingsByRoleIds(GUILD_ID, List.of("role-2"));
        verify(hook).editOriginal("setup.delete.completed:1,0,1");
    }

    @Test
    void pendingDeletion_replyWaitsUntilCallbackFires() {
        Role role = deletableRole("role-1");
        AuditableRestAction<Void> action = mockDeleteAction(role);
        when(roleMappingService.getMappedRoles(GUILD_ID)).thenReturn(List.of(new MappedRole("role-1", true)));

        deleteCommand.onDelete(event);

        verify(action).queue(any(), any());
        verify(hook, never()).editOriginal(anyString());
    }

    @Test
    void noMappings_repliesNoRoles() {
        when(roleMappingService.getMappedRoles(GUILD_ID)).thenReturn(List.of());

        deleteCommand.onDelete(event);

        verify(hook).editOriginal("setup.delete.no_roles:");
    }

    @Test
    void missingManageRoles_rejectedWithoutTouchingMappings() {
        when(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(false);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        when(event.reply(anyString())).thenReturn(reply);
        when(reply.setEphemeral(true)).thenReturn(reply);

        deleteCommand.onDelete(event);

        verify(roleMappingService, never()).getMappedRoles(any());
        verify(event, never()).deferReply(true);
    }

    private Role deletableRole(String id) {
        Role role = mock(Role.class);
        when(guild.getRoleById(id)).thenReturn(role);
        when(selfMember.canInteract(role)).thenReturn(true);
        return role;
    }

    @SuppressWarnings("unchecked")
    private AuditableRestAction<Void> mockDeleteAction(Role role) {
        AuditableRestAction<Void> action = mock(AuditableRestAction.class);
        when(role.delete()).thenReturn(action);
        return action;
    }

    private void succeedDelete(Role role) {
        AuditableRestAction<Void> action = mockDeleteAction(role);
        doAnswer(inv -> {
            Consumer<Void> success = inv.getArgument(0);
            success.accept(null);
            return null;
        }).when(action).queue(any(), any());
    }

    private static String joinArgs(Object[] raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < raw.length; i++) {
            Object arg = raw[i];
            if (arg instanceof Object[] varargs) {
                for (int j = 0; j < varargs.length; j++) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(varargs[j]);
                }
            } else {
                if (sb.length() > 0) sb.append(',');
                sb.append(arg);
            }
        }
        return sb.toString();
    }
}
