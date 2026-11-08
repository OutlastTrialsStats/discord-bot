package com.outlasttrialsstats.discordbot.feature.leaderboard.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardBinding;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import java.util.List;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardMessageDeleteListenerTest {

    private static final String CHANNEL_ID = "channel-1";

    @Mock
    private LeaderboardService leaderboardService;

    @InjectMocks
    private LeaderboardMessageDeleteListener listener;

    @Test
    @SuppressWarnings("unchecked")
    void onMessageDelete_leaderboardPage_removesBindingAndDeletesOtherPages() {
        MessageDeleteEvent event = mock(MessageDeleteEvent.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        when(event.getMessageId()).thenReturn("msg-2");
        when(event.getChannel()).thenReturn(channel);
        when(channel.getId()).thenReturn(CHANNEL_ID);
        when(leaderboardService.removeBindingsByMessageIds(List.of("msg-2"))).thenReturn(List.of(
                binding(List.of("msg-1", "msg-2", "msg-3"))));
        AuditableRestAction<Void> delete = mock(AuditableRestAction.class);
        when(channel.deleteMessageById(anyString())).thenReturn(delete);

        listener.onMessageDelete(event);

        verify(channel).deleteMessageById("msg-1");
        verify(channel).deleteMessageById("msg-3");
        verify(channel, never()).deleteMessageById("msg-2");
    }

    @Test
    void onMessageDelete_unrelatedMessage_deletesNothing() {
        MessageDeleteEvent event = mock(MessageDeleteEvent.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        when(event.getMessageId()).thenReturn("unrelated-msg");
        when(event.getChannel()).thenReturn(channel);
        when(leaderboardService.removeBindingsByMessageIds(List.of("unrelated-msg"))).thenReturn(List.of());

        listener.onMessageDelete(event);

        verify(channel, never()).deleteMessageById(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void onMessageBulkDelete_leaderboardPages_deletesOnlyRemainingPages() {
        MessageBulkDeleteEvent event = mock(MessageBulkDeleteEvent.class);
        GuildMessageChannelUnion channel = mock(GuildMessageChannelUnion.class);
        when(event.getMessageIds()).thenReturn(List.of("msg-1", "msg-2", "other"));
        when(event.getChannel()).thenReturn(channel);
        when(channel.getId()).thenReturn(CHANNEL_ID);
        when(leaderboardService.removeBindingsByMessageIds(List.of("msg-1", "msg-2", "other"))).thenReturn(List.of(
                binding(List.of("msg-1", "msg-2", "msg-3"))));
        AuditableRestAction<Void> delete = mock(AuditableRestAction.class);
        when(channel.deleteMessageById(anyString())).thenReturn(delete);

        listener.onMessageBulkDelete(event);

        verify(channel).deleteMessageById("msg-3");
        verify(channel, never()).deleteMessageById("msg-1");
        verify(channel, never()).deleteMessageById("msg-2");
        verify(delete).queue(any(), any());
    }

    private static LeaderboardBinding binding(List<String> messageIds) {
        return new LeaderboardBinding(1L, "guild-1", StatisticType.PRESTIGE, CHANNEL_ID, messageIds,
                messageIds.size());
    }
}
