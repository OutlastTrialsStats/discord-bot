package com.outlasttrialsstats.discordbot.feature.setup.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
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
import java.util.function.Consumer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.RoleAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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

    @Mock
    private InteractionHook hook;

    @Mock
    private WebhookMessageEditAction<Message> editAction;

    @Mock
    private RoleAction roleAction;

    @Captor
    private ArgumentCaptor<Consumer<Role>> successCaptor;

    @Captor
    private ArgumentCaptor<Consumer<Throwable>> failureCaptor;

    @BeforeEach
    void setUp() {
        when(guild.getId()).thenReturn(GUILD_ID);
        when(event.getGuild()).thenReturn(guild);
        JDA jda = mock(JDA.class);
        lenient().when(event.getJDA()).thenReturn(jda);
        lenient().when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
        lenient().when(event.getMember()).thenReturn(invoker);
        lenient().when(guild.getSelfMember()).thenReturn(selfMember);
        lenient().when(selfMember.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);

        OptionMapping threshold = mock(OptionMapping.class);
        when(threshold.getAsInt()).thenReturn(10);
        when(event.getOption("threshold")).thenReturn(threshold);
        OptionMapping roleOption = mock(OptionMapping.class);
        lenient().when(roleOption.getAsRole()).thenReturn(role);
        lenient().when(event.getOption("role")).thenReturn(roleOption);

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

    @Test
    void noRoleOption_mappedRoleStillExists_reusesItInsteadOfCreating() {
        when(event.getOption("role")).thenReturn(null);
        when(roleMappingService.findRankedMappingRoleId(GUILD_ID, RoleCategory.PRESTIGE, 10))
                .thenReturn(Optional.of(ROLE_ID));
        when(guild.getRoleById(ROLE_ID)).thenReturn(role);
        when(messageService.getMessage(GUILD_ID, "setup.prestige_role.success", ROLE_NAME, 10)).thenReturn("ok");

        roleMappingCommand.onRoleMapping(event, "prestige");

        verify(guild, never()).createRole();
        verify(event).reply("ok");
    }

    @Test
    void noRoleOption_noMapping_createsRoleAndSavesMapping() {
        when(event.getOption("role")).thenReturn(null);
        stubRoleCreation();
        Role created = mockCreatedRole();
        when(messageService.getMessage(GUILD_ID, "setup.prestige_role.success", "Prestige 10+", 10)).thenReturn("ok");

        roleMappingCommand.onRoleMapping(event, "prestige");

        verify(roleAction).queue(successCaptor.capture(), failureCaptor.capture());
        successCaptor.getValue().accept(created);
        verify(roleMappingService).saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "created-id", true);
        verify(hook).editOriginal("ok");
    }

    @Test
    void noRoleOption_creationFails_repliesWithLocalizedError() {
        when(event.getOption("role")).thenReturn(null);
        stubRoleCreation();
        when(messageService.getMessage(GUILD_ID, "setup.role_mapping.create_failed", "Prestige 10+"))
                .thenReturn("create failed");

        roleMappingCommand.onRoleMapping(event, "prestige");

        verify(roleAction).queue(successCaptor.capture(), failureCaptor.capture());
        failureCaptor.getValue().accept(new RuntimeException("Maximum number of roles reached"));
        verify(hook).editOriginal("create failed");
        verify(roleMappingService, never()).saveRankedMapping(any(), any(), anyInt(), any(), anyBoolean());
    }

    @Test
    @SuppressWarnings("unchecked")
    void noRoleOption_savingMappingFails_deletesCreatedRoleAndRepliesWithError() {
        when(event.getOption("role")).thenReturn(null);
        stubRoleCreation();
        Role created = mockCreatedRole();
        AuditableRestAction<Void> deleteAction = mock(AuditableRestAction.class);
        when(created.delete()).thenReturn(deleteAction);
        doThrow(new RuntimeException("db down")).when(roleMappingService)
                .saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, "created-id", true);
        when(messageService.getMessage(GUILD_ID, "setup.role_mapping.create_failed", "Prestige 10+"))
                .thenReturn("create failed");

        roleMappingCommand.onRoleMapping(event, "prestige");

        verify(roleAction).queue(successCaptor.capture(), failureCaptor.capture());
        successCaptor.getValue().accept(created);
        verify(deleteAction).queue(any(), any());
        verify(hook).editOriginal("create failed");
    }

    @Test
    void linkRole_replacesOtherMappedRole_tellsAdminOldRoleStays() {
        Role oldRole = mock(Role.class);
        when(oldRole.getId()).thenReturn("old-id");
        when(oldRole.getName()).thenReturn("Old Role");
        when(roleMappingService.findRankedMappingRoleId(GUILD_ID, RoleCategory.PRESTIGE, 10))
                .thenReturn(Optional.of("old-id"));
        when(guild.getRoleById("old-id")).thenReturn(oldRole);
        when(roleMappingService.findOtherCategoryUsingRole(GUILD_ID, ROLE_ID, RoleCategory.PRESTIGE))
                .thenReturn(Optional.empty());
        when(messageService.getMessage(GUILD_ID, "setup.prestige_role.success", ROLE_NAME, 10)).thenReturn("ok");
        when(messageService.getMessage(GUILD_ID, "setup.role_mapping.old_role_kept", "Old Role")).thenReturn("note");

        roleMappingCommand.onRoleMapping(event, "prestige");

        verify(roleMappingService).saveRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10, ROLE_ID, false);
        verify(event).reply("ok\nnote");
    }

    @Test
    void linkRole_sameRoleAsCurrentMapping_noNotice() {
        when(roleMappingService.findRankedMappingRoleId(GUILD_ID, RoleCategory.PRESTIGE, 10))
                .thenReturn(Optional.of(ROLE_ID));
        when(guild.getRoleById(ROLE_ID)).thenReturn(role);
        when(roleMappingService.findOtherCategoryUsingRole(GUILD_ID, ROLE_ID, RoleCategory.PRESTIGE))
                .thenReturn(Optional.empty());
        when(messageService.getMessage(GUILD_ID, "setup.prestige_role.success", ROLE_NAME, 10)).thenReturn("ok");

        roleMappingCommand.onRoleMapping(event, "prestige");

        verify(event).reply("ok");
    }

    @Test
    void removeMapping_roleStillExists_tellsAdminRoleStaysOnMembers() {
        when(roleMappingService.removeRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10))
                .thenReturn(Optional.of(ROLE_ID));
        when(guild.getRoleById(ROLE_ID)).thenReturn(role);
        when(messageService.getMessage(GUILD_ID, "setup.prestige_role.removed", 10)).thenReturn("removed");
        when(messageService.getMessage(GUILD_ID, "setup.role_mapping.old_role_kept", ROLE_NAME)).thenReturn("note");

        roleMappingCommand.onRemoveRoleMapping(event, "prestige");

        verify(event).reply("removed\nnote");
    }

    @Test
    void removeMapping_noMapping_repliesWithoutNotice() {
        when(roleMappingService.removeRankedMapping(GUILD_ID, RoleCategory.PRESTIGE, 10)).thenReturn(Optional.empty());
        when(messageService.getMessage(GUILD_ID, "setup.prestige_role.removed", 10)).thenReturn("removed");

        roleMappingCommand.onRemoveRoleMapping(event, "prestige");

        verify(event).reply("removed");
    }

    private void stubRoleCreation() {
        when(event.deferReply(true)).thenReturn(replyAction);
        when(event.getHook()).thenReturn(hook);
        lenient().when(hook.editOriginal(anyString())).thenReturn(editAction);
        when(guild.createRole()).thenReturn(roleAction);
        when(roleAction.setName("Prestige 10+")).thenReturn(roleAction);
        when(roleAction.setPermissions(EnumSet.noneOf(Permission.class))).thenReturn(roleAction);
    }

    private Role mockCreatedRole() {
        Role created = mock(Role.class);
        when(created.getId()).thenReturn("created-id");
        when(created.getName()).thenReturn("Prestige 10+");
        return created;
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
