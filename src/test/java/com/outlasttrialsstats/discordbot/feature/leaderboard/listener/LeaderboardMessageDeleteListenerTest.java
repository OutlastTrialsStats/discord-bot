package com.outlasttrialsstats.discordbot.feature.leaderboard.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.LeaderboardChannel;
import com.outlasttrialsstats.discordbot.repository.LeaderboardChannelRepository;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardMessageDeleteListenerTest {

    @Mock
    private LeaderboardChannelRepository leaderboardChannelRepository;

    @InjectMocks
    private LeaderboardMessageDeleteListener listener;

    @Test
    void onMessageDelete_leaderboardMessage_removesBinding() {
        MessageDeleteEvent event = mock(MessageDeleteEvent.class);
        when(event.getMessageId()).thenReturn("msg-123");

        LeaderboardChannel binding = new LeaderboardChannel(
                "guild-1", StatisticType.PRESTIGE, "channel-1", List.of("msg-123"), 1);
        when(leaderboardChannelRepository.findByMessageId("msg-123"))
                .thenReturn(Optional.of(binding));

        listener.onMessageDelete(event);

        verify(leaderboardChannelRepository).delete(binding);
    }

    @Test
    void onMessageDelete_unrelatedMessage_doesNothing() {
        MessageDeleteEvent event = mock(MessageDeleteEvent.class);
        when(event.getMessageId()).thenReturn("unrelated-msg");

        when(leaderboardChannelRepository.findByMessageId("unrelated-msg"))
                .thenReturn(Optional.empty());

        listener.onMessageDelete(event);

        verify(leaderboardChannelRepository, never()).delete(any());
    }
}
