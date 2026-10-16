package com.outlasttrialsstats.discordbot.feature.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.utils.concurrent.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuildMemberLoaderTest {

    @Mock
    private Guild guild;

    @Mock
    private Task<List<Member>> task;

    private final GuildMemberLoader loader = new GuildMemberLoader();

    @BeforeEach
    void setUp() {
        when(guild.loadMembers()).thenReturn(task);
        when(task.setTimeout(any(Duration.class))).thenReturn(task);
    }

    @Test
    void load_returnsMembersWithExtendedTimeout() {
        var members = List.of(mock(Member.class));
        when(task.get()).thenReturn(members);

        assertThat(loader.load(guild)).isEqualTo(members);
        verify(task).setTimeout(Duration.ofSeconds(60));
    }

    @Test
    void load_timeout_retriesOnce() {
        var members = List.of(mock(Member.class));
        when(task.get())
                .thenThrow(new CompletionException(new TimeoutException()))
                .thenReturn(members);

        assertThat(loader.load(guild)).isEqualTo(members);
        verify(guild, times(2)).loadMembers();
    }

    @Test
    void load_repeatedTimeout_throws() {
        when(task.get()).thenThrow(new CompletionException(new TimeoutException()));

        assertThatThrownBy(() -> loader.load(guild)).hasCauseInstanceOf(TimeoutException.class);
        verify(guild, times(2)).loadMembers();
    }

    @Test
    void load_otherFailure_doesNotRetry() {
        when(task.get()).thenThrow(new CompletionException(new IllegalStateException("boom")));

        assertThatThrownBy(() -> loader.load(guild)).hasCauseInstanceOf(IllegalStateException.class);
        verify(guild, times(1)).loadMembers();
    }
}
