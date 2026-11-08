package com.outlasttrialsstats.discordbot.feature.setup.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.EnumSet;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleMappingCommandTest {

    private static final String GUILD_ID = "guild-1";
    private static final String ROLE_ID = "role-1";
    private static final String ROLE_NAME = "Some Role";

    @Mock
    private RoleMappingService roleMappingService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private RoleMappingCommand roleMappingCommand;

    @Mock
    private SlashCommandInteractionEvent event;

    @Mock
    private Guild guild;

    @Mock
    private SelfMember selfMember;

    @Mock
    private Member invoker;

    @Mock
    private Role role;

    @Mock
    private ReplyCallbackAction replyAction;

    @BeforeEach
    void setUp() {
        when(guild.getId()).thenReturn(GUILD_ID);
        when(event.getGuild()).thenReturn(guild);
        JDA jda = mock(JDA.class);
        when(event.getJDA()).thenReturn(jda);
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
        lenient().when(event.getMember()).thenReturn(invoker);
        lenient().when(guild.getSelfMember()).thenReturn(selfMember);
        lenient().when(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);

        OptionMapping threshold = mock(OptionMapping.class);
        when(threshold.getAsInt()).thenReturn(10);
        when(event.getOption("threshold")).thenReturn(threshold);
        OptionMapping roleOption = mock(OptionMapping.class);
        when(roleOption.getAsRole()).thenReturn(role);
        when(event.getOption("role")).thenReturn(roleOption);

        lenient().when(role.getId()).thenReturn(ROLE_ID);
        lenient().when(role.getName()).thenReturn(ROLE_NAME);
        lenient().when(role.getPermissions()).thenReturn(EnumSet.noneOf(Permission.class));
        lenient().when(selfMember.canInteract(role)).thenReturn(true);
        lenient().when(invoker.canInteract(role)).thenReturn(true);

        lenient().when(event.reply(any(String.class))).thenReturn(replyAction);
        lenient().when(replyAction.setEphemeral(true)).thenReturn(replyAction);
    }

    @Test
    void linkSafeRole_savesMappingAndReplies() {
        when(roleMappingService.findOtherCategoryUsingRole(GUILD_ID, ROLE_ID, RoleCategory.PRESTIGE))
                .thenReturn(Optional.empty());
        when(messageService.getMessage(GUILD_ID, "setup.prestige_role.success", ROLE_NAME, 10)).thenReturn("ok");

        roleMappingCommand.onRoleMapping(event, "prestige");

        verify(roleMappingService).saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, ROLE_ID, false);
        verify(event).reply("ok");
    }

    @Test
    void linkPublicRole_rejected() {
        when(role.isPublicRole()).thenReturn(true);
        expectRejection("error.role_unsafe.public");
    }

    @Test
    void linkManagedRole_rejected() {
        when(role.isManaged()).thenReturn(true);
        expectRejection("error.role_unsafe.managed");
    }

    @Test
    void linkRoleAboveBot_rejected() {
        when(selfMember.canInteract(role)).thenReturn(false);
        expectRejection("error.role_unsafe.bot_hierarchy");
    }

    @Test
    void linkRoleAboveInvoker_rejected() {
        when(invoker.canInteract(role)).thenReturn(false);
        expectRejection("error.role_unsafe.invoker_hierarchy");
    }

    @Test
    void linkRoleWithAdministrator_rejected() {
        when(role.getPermissions()).thenReturn(EnumSet.of(Permission.ADMINISTRATOR));
        expectRejection("error.role_unsafe.dangerous_permissions");
    }

    @Test
    void linkRoleWithBanMembers_rejected() {
        when(role.getPermissions()).thenReturn(EnumSet.of(Permission.MESSAGE_SEND, Permission.BAN_MEMBERS));
        expectRejection("error.role_unsafe.dangerous_permissions");
    }

    @Test
    void linkRoleMappedInOtherCategory_rejected() {
        when(roleMappingService.findOtherCategoryUsingRole(GUILD_ID, ROLE_ID, RoleCategory.PRESTIGE))
                .thenReturn(Optional.of(RoleCategory.LEVEL));
        when(messageService.getMessage(GUILD_ID, "error.role_already_mapped",
                ROLE_NAME, RoleCategory.LEVEL.getDisplayName())).thenReturn("already mapped");

        roleMappingCommand.onRoleMapping(event, "prestige");

        verify(event).reply("already mapped");
        verify(roleMappingService, never()).saveRankedMapping(any(), any(), anyInt(), any(), anyBoolean());
    }

    @Test
    void linkRole_botLacksManageRoles_rejected() {
        when(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(false);
        when(messageService.getMessage(GUILD_ID, "error.missing_permission.manage_roles")).thenReturn("no perm");

        roleMappingCommand.onRoleMapping(event, "prestige");

        verify(event).reply("no perm");
        verify(roleMappingService, never()).saveRankedMapping(any(), any(), anyInt(), any(), anyBoolean());
    }

    private void expectRejection(String messageKey) {
        when(messageService.getMessage(GUILD_ID, messageKey, ROLE_NAME)).thenReturn("rejected");

        roleMappingCommand.onRoleMapping(event, "prestige");

        verify(event).reply("rejected");
        verify(replyAction).setEphemeral(true);
        verify(roleMappingService, never()).saveRankedMapping(any(), any(), anyInt(), any(), anyBoolean());
        verify(roleMappingService, never()).findOtherCategoryUsingRole(any(), any(), any());
    }
}
