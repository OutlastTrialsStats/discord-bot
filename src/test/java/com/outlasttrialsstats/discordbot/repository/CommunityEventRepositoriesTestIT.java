package com.outlasttrialsstats.discordbot.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outlasttrialsstats.discordbot.IntegrationTest;
import com.outlasttrialsstats.discordbot.entity.CommunityAlertType;
import com.outlasttrialsstats.discordbot.entity.CommunityEventAlertRule;
import com.outlasttrialsstats.discordbot.entity.CommunityEventAlertState;
import com.outlasttrialsstats.discordbot.entity.CommunityEventMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class CommunityEventRepositoriesTestIT {

    private static final String GUILD_ID = "guild-1";
    private static final String OTHER_GUILD_ID = "guild-2";

    @Autowired
    private CommunityEventMessageRepository messageRepository;
    @Autowired
    private CommunityEventAlertRuleRepository ruleRepository;
    @Autowired
    private CommunityEventAlertStateRepository stateRepository;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        ruleRepository.deleteAll();
        stateRepository.deleteAll();
    }

    @Test
    void message_findByMessageIdIn() {
        messageRepository.save(new CommunityEventMessage(GUILD_ID, null, "e", "channel-1", "msg-1"));
        messageRepository.save(new CommunityEventMessage(OTHER_GUILD_ID, "e", "e", "channel-2", "msg-2"));

        assertThat(messageRepository.findByMessageIdIn(List.of("msg-2", "unknown")))
                .extracting(CommunityEventMessage::getGuildId)
                .containsExactly(OTHER_GUILD_ID);
    }

    @Test
    void message_severalTargetsPerGuild() {
        messageRepository.save(new CommunityEventMessage(GUILD_ID, null, "e", "channel-1", "msg-1"));
        messageRepository.save(new CommunityEventMessage(GUILD_ID, "counter", "counter", "channel-2", "msg-2"));

        assertThat(messageRepository.findByGuildIdOrderByIdAsc(GUILD_ID))
                .extracting(CommunityEventMessage::getMessageId)
                .containsExactly("msg-1", "msg-2");
        assertThat(messageRepository.findByGuildIdAndEventIdIsNull(GUILD_ID))
                .map(CommunityEventMessage::getMessageId).contains("msg-1");
        assertThat(messageRepository.findByGuildIdAndEventId(GUILD_ID, "counter"))
                .map(CommunityEventMessage::getMessageId).contains("msg-2");
        assertThat(messageRepository.countByGuildId(GUILD_ID)).isEqualTo(2);
    }

    @Test
    void message_uniquePerGuildAndTarget() {
        messageRepository.saveAndFlush(new CommunityEventMessage(GUILD_ID, null, "e", "channel-1", "msg-1"));
        messageRepository.saveAndFlush(new CommunityEventMessage(OTHER_GUILD_ID, null, "e", "channel-1", "msg-2"));

        assertThatThrownBy(() -> messageRepository.saveAndFlush(
                new CommunityEventMessage(GUILD_ID, null, "e", "channel-3", "msg-3")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rule_newRuleUsesTypeDefaults() {
        CommunityEventAlertRule saved = ruleRepository.save(
                new CommunityEventAlertRule(GUILD_ID, CommunityAlertType.PRE_ALERT));

        CommunityEventAlertRule loaded = ruleRepository
                .findByGuildIdAndAlertType(GUILD_ID, CommunityAlertType.PRE_ALERT).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.isEnabled()).isTrue();
        assertThat(loaded.getLeadTimeMinutes()).isEqualTo(60);
        assertThat(loaded.getRoleId()).isNull();
    }

    @Test
    void rule_uniquePerGuildAndType() {
        ruleRepository.saveAndFlush(new CommunityEventAlertRule(GUILD_ID, CommunityAlertType.PRE_ALERT));

        assertThatThrownBy(() -> ruleRepository.saveAndFlush(
                new CommunityEventAlertRule(GUILD_ID, CommunityAlertType.PRE_ALERT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rule_clearRoleOnlyTouchesThatRoleInThatGuild() {
        ruleRepository.save(rule(GUILD_ID, CommunityAlertType.PRE_ALERT, "role-1"));
        ruleRepository.save(rule(GUILD_ID, CommunityAlertType.MILESTONE_REACHED, "role-2"));
        ruleRepository.save(rule(OTHER_GUILD_ID, CommunityAlertType.PRE_ALERT, "role-1"));
        ruleRepository.flush();

        int cleared = ruleRepository.clearRole(GUILD_ID, "role-1");

        assertThat(cleared).isEqualTo(1);
        assertThat(roleOf(GUILD_ID, CommunityAlertType.PRE_ALERT)).isNull();
        assertThat(roleOf(GUILD_ID, CommunityAlertType.MILESTONE_REACHED)).isEqualTo("role-2");
        assertThat(roleOf(OTHER_GUILD_ID, CommunityAlertType.PRE_ALERT)).isEqualTo("role-1");
    }

    @Test
    void state_deleteByGuildId() {
        stateRepository.save(new CommunityEventAlertState(GUILD_ID, "e", 1, 2));
        stateRepository.save(new CommunityEventAlertState(OTHER_GUILD_ID, "e", 0, 0));

        stateRepository.deleteByGuildId(GUILD_ID);

        assertThat(stateRepository.findByGuildId(GUILD_ID)).isEmpty();
        assertThat(stateRepository.findByGuildId(OTHER_GUILD_ID)).hasSize(1);
    }

    private static CommunityEventAlertRule rule(String guildId, CommunityAlertType type, String roleId) {
        CommunityEventAlertRule rule = new CommunityEventAlertRule(guildId, type);
        rule.setRoleId(roleId);
        return rule;
    }

    private String roleOf(String guildId, CommunityAlertType type) {
        return ruleRepository.findByGuildIdAndAlertType(guildId, type).orElseThrow().getRoleId();
    }
}
