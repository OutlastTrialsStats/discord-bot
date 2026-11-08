package com.outlasttrialsstats.discordbot.feature.setup.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.discordbot.feature.setup.service.BasicSetupService;
import com.outlasttrialsstats.discordbot.feature.setup.service.BasicSetupService.SetupResult;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StartBasicCommandTest {

    private static final String GUILD_ID = "guild-1";

    @Mock
    private BasicSetupService basicSetupService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private StartBasicCommand startBasicCommand;

    @Test
    void buildResultMessage_createdAndFailed_listsBoth() {
        when(messageService.getMessage(GUILD_ID, "setup.basic.completed", 2, "A, B")).thenReturn("created");
        when(messageService.getMessage(GUILD_ID, "setup.basic.failed", 1, "C")).thenReturn("failed");

        String message = startBasicCommand.buildResultMessage(GUILD_ID,
                new SetupResult(List.of("A", "B"), List.of("C")));

        assertThat(message).isEqualTo("created\nfailed");
    }

    @Test
    void buildResultMessage_onlyFailed_showsOnlyFailures() {
        when(messageService.getMessage(GUILD_ID, "setup.basic.failed", 1, "C")).thenReturn("failed");

        String message = startBasicCommand.buildResultMessage(GUILD_ID, new SetupResult(List.of(), List.of("C")));

        assertThat(message).isEqualTo("failed");
    }
}
