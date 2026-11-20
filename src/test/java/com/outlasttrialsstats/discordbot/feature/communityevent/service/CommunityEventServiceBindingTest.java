package com.outlasttrialsstats.discordbot.feature.communityevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.entity.CommunityEventMessage;
import com.outlasttrialsstats.discordbot.repository.CommunityEventAlertConfigRepository;
import com.outlasttrialsstats.discordbot.repository.CommunityEventAlertRuleRepository;
import com.outlasttrialsstats.discordbot.repository.CommunityEventAlertStateRepository;
import com.outlasttrialsstats.discordbot.repository.CommunityEventMessageRepository;
import com.outlasttrialsstats.discordbot.shared.TOTStatsApiClient;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommunityEventServiceBindingTest {

    private static final String GUILD_ID = "guild-1";

    private final CommunityEventMessageRepository messageRepository = mock(CommunityEventMessageRepository.class);
    private CommunityEventService service;

    @BeforeEach
    void setUp() {
        service = new CommunityEventService(mock(TOTStatsApiClient.class), messageRepository,
                mock(CommunityEventAlertConfigRepository.class), mock(CommunityEventAlertRuleRepository.class),
                mock(CommunityEventAlertStateRepository.class));
    }

    @Test
    void savingTheSameTargetReplacesTheExistingMessage() {
        CommunityEventMessage existing = new CommunityEventMessage(GUILD_ID, null, "old", "channel-1", "msg-1");
        when(messageRepository.findByGuildIdAndEventIdIsNull(GUILD_ID)).thenReturn(Optional.of(existing));

        CommunityEventService.SaveResult result = service.saveBinding(GUILD_ID, null, "new", "channel-2", "msg-2");

        assertThat(result).isInstanceOfSatisfying(CommunityEventService.SaveResult.Saved.class, saved ->
                assertThat(saved.previous()).map(CommunityEventBinding::messageId).contains("msg-1"));
        assertThat(existing.getMessageId()).isEqualTo("msg-2");
        assertThat(existing.getChannelId()).isEqualTo("channel-2");
        verify(messageRepository).save(existing);
    }

    @Test
    void aNewTargetIsAddedNextToTheExistingOnes() {
        when(messageRepository.findByGuildIdAndEventId(GUILD_ID, "counter")).thenReturn(Optional.empty());
        when(messageRepository.countByGuildId(GUILD_ID)).thenReturn(1L);

        CommunityEventService.SaveResult result = service.saveBinding(GUILD_ID, "counter", "counter", "c", "m");

        assertThat(result).isInstanceOfSatisfying(CommunityEventService.SaveResult.Saved.class, saved ->
                assertThat(saved.previous()).isEmpty());
        verify(messageRepository).save(any(CommunityEventMessage.class));
    }

    @Test
    void aNewTargetBeyondTheLimitIsRejected() {
        when(messageRepository.findByGuildIdAndEventId(GUILD_ID, "counter")).thenReturn(Optional.empty());
        when(messageRepository.countByGuildId(GUILD_ID))
                .thenReturn((long) CommunityEventService.MAX_MESSAGES_PER_GUILD);

        CommunityEventService.SaveResult result = service.saveBinding(GUILD_ID, "counter", "counter", "c", "m");

        assertThat(result).isEqualTo(
                new CommunityEventService.SaveResult.LimitReached(CommunityEventService.MAX_MESSAGES_PER_GUILD));
        assertThat(service.hasRoomFor(GUILD_ID, "counter")).isFalse();
        verify(messageRepository, never()).save(any());
    }

    @Test
    void replacingAnExistingTargetIsAllowedAtTheLimit() {
        when(messageRepository.findByGuildIdAndEventIdIsNull(GUILD_ID))
                .thenReturn(Optional.of(new CommunityEventMessage(GUILD_ID, null, "e", "c", "m")));
        when(messageRepository.countByGuildId(GUILD_ID))
                .thenReturn((long) CommunityEventService.MAX_MESSAGES_PER_GUILD);

        assertThat(service.hasRoomFor(GUILD_ID, null)).isTrue();
    }

    @Test
    void removingAMessageOfAnotherGuildDoesNothing() {
        CommunityEventMessage other = new CommunityEventMessage("guild-2", null, "e", "c", "m");
        when(messageRepository.findById(7L)).thenReturn(Optional.of(other));

        assertThat(service.removeBinding(GUILD_ID, 7L)).isEmpty();
        verify(messageRepository, never()).delete(any());
    }
}
