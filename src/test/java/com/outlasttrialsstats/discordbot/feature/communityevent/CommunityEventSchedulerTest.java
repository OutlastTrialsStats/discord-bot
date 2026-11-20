package com.outlasttrialsstats.discordbot.feature.communityevent;

import static com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEventTestData.liveEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import com.outlasttrialsstats.discordbot.entity.CommunityAlertType;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.AlertRule;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.CommunityAlertEvaluator.AlertProgress;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventBinding;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommunityEventSchedulerTest {

    private static final String GUILD_ID = "guild-1";
    private static final String CHANNEL_ID = "channel-1";
    private static final String ROLE_ID = "role-1";
    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    @Mock
    private JDA jda;
    @Mock
    private CommunityEventService service;
    @Mock
    private CommunityEventEmbedFactory embedFactory;
    @Mock
    private Guild guild;
    @Mock
    private SelfMember selfMember;
    @Mock
    private StandardGuildMessageChannel channel;
    @Mock
    private MessageCreateAction createAction;
    @Mock
    private MessageEditAction editAction;

    private CommunityEventScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CommunityEventScheduler(jda, service, embedFactory, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
        lenient().when(guild.getId()).thenReturn(GUILD_ID);
        lenient().when(guild.getSelfMember()).thenReturn(selfMember);
        lenient().when(guild.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(channel);
        lenient().when(selfMember.hasPermission(eq(channel), any(Permission[].class))).thenReturn(true);
        lenient().when(embedFactory.buildAlertEmbed(any(), any(), any())).thenReturn(mock(MessageEmbed.class));
        lenient().when(channel.sendMessage(any(MessageCreateData.class))).thenReturn(createAction);
    }

    @Test
    void processAlerts_savesProgressBeforeSendingWithRolePing() {
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(ROLE_ID);
        when(role.getAsMention()).thenReturn("<@&" + ROLE_ID + ">");
        when(guild.getRoleById(ROLE_ID)).thenReturn(role);
        Map<CommunityAlertType, AlertRule> rules = Map.of(CommunityAlertType.MILESTONE_REACHED,
                new AlertRule(CommunityAlertType.MILESTONE_REACHED, true, ROLE_ID, null));

        scheduler.processAlerts(GUILD_ID, CHANNEL_ID, List.of(liveEvent("e", 2_100, null)), rules,
                Map.of("e", new AlertProgress(0, 0)), NOW);

        ArgumentCaptor<MessageCreateData> sent = ArgumentCaptor.forClass(MessageCreateData.class);
        InOrder order = inOrder(service, channel);
        order.verify(service).saveProgress(GUILD_ID, Map.of("e", new AlertProgress(1, 1)));
        order.verify(channel).sendMessage(sent.capture());
        assertThat(sent.getValue().getContent()).isEqualTo("<@&" + ROLE_ID + ">");
        assertThat(sent.getValue().getMentionedRoles()).containsExactly(ROLE_ID);
        assertThat(sent.getValue().getAllowedMentions()).isEmpty();
    }

    @Test
    void processAlerts_firstObservationOnlyRecordsProgress() {
        scheduler.processAlerts(GUILD_ID, CHANNEL_ID, List.of(liveEvent("e", 2_100, null)), Map.of(), Map.of(), NOW);

        verify(service).saveProgress(GUILD_ID, Map.of("e", new AlertProgress(1, 1)));
        verify(channel, never()).sendMessage(any(MessageCreateData.class));
    }

    @Test
    void processAlerts_missingPermissionDropsAlertsButKeepsProgress() {
        when(selfMember.hasPermission(eq(channel), any(Permission[].class))).thenReturn(false);

        scheduler.processAlerts(GUILD_ID, CHANNEL_ID, List.of(liveEvent("e", 2_100, null)), Map.of(),
                Map.of("e", new AlertProgress(0, 0)), NOW);

        verify(service).saveProgress(eq(GUILD_ID), anyMap());
        verify(channel, never()).sendMessage(any(MessageCreateData.class));
    }

    @Test
    void processAlerts_deletedChannelTurnsAlertsOff() {
        when(guild.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(null);

        scheduler.processAlerts(GUILD_ID, CHANNEL_ID, List.of(liveEvent("e", 2_100, null)), Map.of(), Map.of(), NOW);

        verify(service).removeAlertChannelIf(GUILD_ID, CHANNEL_ID);
        verify(service, never()).saveProgress(any(), any());
    }

    @Test
    void applyPing_everyoneRoleUsesEveryoneMention() {
        Role everyone = mock(Role.class);
        when(everyone.isPublicRole()).thenReturn(true);
        when(guild.getRoleById(GUILD_ID)).thenReturn(everyone);
        MessageCreateBuilder message = new MessageCreateBuilder().setEmbeds(mock(MessageEmbed.class));

        CommunityEventScheduler.applyPing(message, guild,
                new AlertRule(CommunityAlertType.PRE_ALERT, true, GUILD_ID, 60));

        MessageCreateData data = message.build();
        assertThat(data.getContent()).isEqualTo("@everyone");
        assertThat(data.getAllowedMentions()).isEqualTo(EnumSet.of(Message.MentionType.EVERYONE));
    }

    @Test
    void applyPing_withoutRoleMentionsNothing() {
        MessageCreateBuilder message = new MessageCreateBuilder().setEmbeds(mock(MessageEmbed.class));

        CommunityEventScheduler.applyPing(message, guild, AlertRule.defaults(CommunityAlertType.PRE_ALERT));

        MessageCreateData data = message.build();
        assertThat(data.getContent()).isEmpty();
        assertThat(data.getAllowedMentions()).isEmpty();
    }

    @Test
    void updateMessage_skipsEditWhenNothingChanged() {
        DiscordCommunityEvent event = liveEvent("e", 1_500, null);
        CommunityEventBinding binding = new CommunityEventBinding(GUILD_ID, null, "e", CHANNEL_ID, "msg-1");
        MessageEmbed embed = mock(MessageEmbed.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(embed.toData().toString()).thenReturn("same");
        when(embedFactory.buildProgressEmbed(GUILD_ID, event, NOW)).thenReturn(embed);
        when(jda.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(channel);
        when(channel.getGuild()).thenReturn(guild);
        when(selfMember.hasAccess(channel)).thenReturn(true);
        when(channel.editMessageEmbedsById("msg-1", embed)).thenReturn(editAction);
        when(editAction.setComponents()).thenReturn(editAction);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<Message> success = invocation.getArgument(0);
            success.accept(mock(Message.class));
            return null;
        }).when(editAction).queue(any(), any());

        scheduler.updateMessage(binding, List.of(event), NOW);
        scheduler.updateMessage(binding, List.of(event), NOW);

        verify(channel, times(1)).editMessageEmbedsById("msg-1", embed);
    }

    @Test
    void updateMessage_autoFollowSwitchesToNewEvent() {
        DiscordCommunityEvent newEvent = liveEvent("new", 0, null)
                .startsAt(Instant.parse("2026-11-05T00:00:00Z").atOffset(ZoneOffset.UTC));
        DiscordCommunityEvent oldEvent = liveEvent("old", 4_100, null).active(false)
                .endsAt(Instant.parse("2026-11-04T00:00:00Z").atOffset(ZoneOffset.UTC));
        CommunityEventBinding binding = new CommunityEventBinding(GUILD_ID, null, "old", CHANNEL_ID, "msg-old");
        Message posted = mock(Message.class);
        when(posted.getId()).thenReturn("msg-new");
        when(jda.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(channel);
        when(channel.getGuild()).thenReturn(guild);
        when(selfMember.hasAccess(channel)).thenReturn(true);
        when(channel.editMessageEmbedsById(eq("msg-old"), any(MessageEmbed.class))).thenReturn(editAction);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(createAction);
        when(editAction.setComponents()).thenReturn(editAction);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<Message> success = invocation.getArgument(0);
            success.accept(posted);
            return null;
        }).when(createAction).queue(any(), any());
        when(embedFactory.buildProgressEmbed(any(), any(), any())).thenReturn(mock(MessageEmbed.class));
        when(service.switchShownEvent(GUILD_ID, "msg-old", "new", "msg-new")).thenReturn(true);

        scheduler.updateMessage(binding, List.of(oldEvent, newEvent), NOW);

        verify(embedFactory).buildProgressEmbed(GUILD_ID, oldEvent, NOW);
        verify(service).switchShownEvent(GUILD_ID, "msg-old", "new", "msg-new");
    }

    @Test
    void updateMessage_deletedChannelRemovesBinding() {
        CommunityEventBinding binding = new CommunityEventBinding(GUILD_ID, null, "e", CHANNEL_ID, "msg-1");
        when(jda.getChannelById(StandardGuildMessageChannel.class, CHANNEL_ID)).thenReturn(null);
        when(jda.getUnavailableGuilds()).thenReturn(Set.of());

        scheduler.updateMessage(binding, List.of(liveEvent("e", 1_500, null)), NOW);

        verify(service).removeBindingIfMessage(GUILD_ID, "msg-1");
    }

    @Test
    void processAlerts_guildLeftTurnsAlertsOff() {
        when(jda.getGuildById(GUILD_ID)).thenReturn(null);
        when(jda.getUnavailableGuilds()).thenReturn(Set.of());

        scheduler.processAlerts(GUILD_ID, CHANNEL_ID, List.of(liveEvent("e", 2_100, null)), Map.of(), Map.of(), NOW);

        verify(service).removeAlertChannelIf(GUILD_ID, CHANNEL_ID);
    }

    @Test
    void isAlertRelevant_liveAndJustEndedEventsOnly() {
        DiscordCommunityEvent justEnded = liveEvent("e", 1_000, null).active(false)
                .endsAt(NOW.minusSeconds(600).atOffset(ZoneOffset.UTC));
        DiscordCommunityEvent longEnded = liveEvent("e", 1_000, null).active(false)
                .endsAt(NOW.minusSeconds(7200).atOffset(ZoneOffset.UTC));

        assertThat(CommunityEventScheduler.isAlertRelevant(liveEvent("e", 1_000, null), NOW)).isTrue();
        assertThat(CommunityEventScheduler.isAlertRelevant(justEnded, NOW)).isTrue();
        assertThat(CommunityEventScheduler.isAlertRelevant(longEnded, NOW)).isFalse();
    }
}
