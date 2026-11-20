package com.outlasttrialsstats.discordbot.feature.setup.panel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.CommunityAlertType;
import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.AlertRule;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventBinding;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventSettings;
import com.outlasttrialsstats.discordbot.feature.leaderboard.LeaderboardCategories.Group;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardBinding;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardSetupService;
import com.outlasttrialsstats.discordbot.feature.profile.command.SyncAllCommand;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.RoleTarget;
import com.outlasttrialsstats.discordbot.feature.setup.command.StartBasicCommand;
import com.outlasttrialsstats.discordbot.feature.setup.service.GuildSettingsService;
import com.outlasttrialsstats.discordbot.feature.setup.service.MessageFileService;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleCleanupService;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingActions;
import com.outlasttrialsstats.discordbot.feature.setup.service.RoleMappingService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.attribute.ICustomId;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SetupPanelLayoutTest {

    private static final String GUILD_ID = "guild-1";

    private final MessageService messageService = mock(MessageService.class, invocation ->
            "getMessage".equals(invocation.getMethod().getName()) ? invocation.getArgument(1) : null);
    private final PanelSupport support = new PanelSupport(messageService);
    private final Guild guild = mock(Guild.class);

    private final GuildSettingsService settingsService = mock(GuildSettingsService.class);
    private final RoleMappingActions roleActions = mock(RoleMappingActions.class);
    private final RoleMappingService roleMappingService = mock(RoleMappingService.class);
    private final CommunityEventSettings eventSettings = mock(CommunityEventSettings.class);
    private final LeaderboardSetupService leaderboardSetupService = mock(LeaderboardSetupService.class);
    private final LeaderboardService leaderboardService = mock(LeaderboardService.class);

    private HomeSection home;
    private GeneralSection general;
    private MessagesSection messages;
    private RolesSection roles;
    private EventSection event;
    private LeaderboardsSection leaderboards;

    @BeforeEach
    void setUp() {
        when(guild.getId()).thenReturn(GUILD_ID);
        when(settingsService.get(GUILD_ID)).thenReturn(new GuildServer(GUILD_ID));
        when(leaderboardService.getCategoryDisplayName(any(), any()))
                .thenAnswer(inv -> "Category " + ((StatisticType) inv.getArgument(1)).getValue());

        List<RoleMappingActions.Mapping> manyMappings = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> new RoleMappingActions.Mapping(
                        RoleTarget.parse(RoleCategory.PRESTIGE, String.valueOf(i)).orElseThrow(), "role-" + i))
                .toList();
        when(roleActions.list(GUILD_ID, RoleCategory.PRESTIGE)).thenReturn(manyMappings);
        when(roleActions.list(anyString(), any())).thenAnswer(inv ->
                inv.getArgument(1) == RoleCategory.PRESTIGE ? manyMappings : List.of());

        List<CommunityEventBinding> bindings = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new CommunityEventBinding((long) i, GUILD_ID, null, "event-" + i, "channel-" + i,
                        "message-" + i))
                .toList();
        when(eventSettings.bindings(GUILD_ID)).thenReturn(bindings);
        when(eventSettings.bindingLabel(any(), any())).thenReturn("x".repeat(150));
        when(eventSettings.knownEvents()).thenReturn(IntStream.rangeClosed(1, 40)
                .mapToObj(i -> new DiscordCommunityEvent().id("event-" + i).name("Event " + i)).toList());
        when(eventSettings.alertChannelId(GUILD_ID)).thenReturn(Optional.of("123456789012345678"));
        Map<CommunityAlertType, AlertRule> rules = new EnumMap<>(CommunityAlertType.class);
        for (CommunityAlertType type : CommunityAlertType.values()) {
            rules.put(type, AlertRule.defaults(type));
        }
        when(eventSettings.rules(GUILD_ID)).thenReturn(rules);
        when(eventSettings.typeName(any(), any())).thenAnswer(inv -> inv.getArgument(1).toString());
        when(eventSettings.statusEmbed(guild)).thenReturn(new EmbedBuilder().setDescription("status").build());

        List<LeaderboardBinding> leaderboardBindings = new ArrayList<>();
        for (StatisticType type : StatisticType.values()) {
            leaderboardBindings.add(new LeaderboardBinding((long) type.ordinal(), GUILD_ID, type, "channel",
                    List.of("m"), 3));
        }
        when(leaderboardSetupService.list(GUILD_ID)).thenReturn(leaderboardBindings);

        home = new HomeSection(support, roleMappingService, eventSettings, leaderboardSetupService, settingsService,
                mock(StartBasicCommand.class));
        general = new GeneralSection(support, settingsService, mock(SyncAllCommand.class),
                mock(RoleCleanupService.class));
        messages = new MessagesSection(support, mock(MessageFileService.class));
        roles = new RolesSection(support, roleActions, roleMappingService);
        event = new EventSection(support, eventSettings);
        leaderboards = new LeaderboardsSection(support, leaderboardSetupService, leaderboardService);
    }

    @Test
    void mainViewsFitDiscordLimits() {
        for (PanelSection section : List.of(home, general, messages, roles, event, leaderboards)) {
            assertFitsDiscordLimits(section.render(guild));
        }
    }

    @ParameterizedTest
    @EnumSource(RoleCategory.class)
    void roleCategoryViewsFitDiscordLimits(RoleCategory category) {
        assertFitsDiscordLimits(roles.render(guild, category));
    }

    @Test
    void eventSubViewsFitDiscordLimits() {
        assertFitsDiscordLimits(event.renderPost(guild, EventSection.CURRENT));
        assertFitsDiscordLimits(event.renderPost(guild, "event-3"));
        assertFitsDiscordLimits(event.renderAlerts(guild));
    }

    @ParameterizedTest
    @EnumSource(Group.class)
    void leaderboardAddViewsFitDiscordLimits(Group group) {
        StatisticType first = com.outlasttrialsstats.discordbot.feature.leaderboard.LeaderboardCategories
                .inGroup(group).getFirst();
        assertFitsDiscordLimits(leaderboards.renderAdd(guild, new LeaderboardsSection.Draft(group, null, 3)));
        assertFitsDiscordLimits(leaderboards.renderAdd(guild, new LeaderboardsSection.Draft(group, first, 10)));
    }

    @Test
    void leaderboardDraftSurvivesTheComponentId() {
        var draft = new LeaderboardsSection.Draft(Group.INVASION, StatisticType.INVASION_POINTS, 7);
        PanelAction action = PanelAction.parse(PanelAction.id("leaderboards", "post", draft.args())).orElseThrow();

        assertThat(LeaderboardsSection.Draft.parse(action)).isEqualTo(draft);
    }

    @Test
    void leaderboardDraftDropsCategoriesFromAnotherGroup() {
        PanelAction action = PanelAction.parse("setup:panel:leaderboards:post:TRIALS:INVASION_POINTS:3").orElseThrow();

        assertThat(LeaderboardsSection.Draft.parse(action).category()).isNull();
    }

    @Test
    void roleRemoveMenu_pagesThroughMoreThan25Mappings() {
        PanelView first = roles.render(guild, RoleCategory.PRESTIGE, 0);
        PanelView second = roles.render(guild, RoleCategory.PRESTIGE, 1);

        assertFitsDiscordLimits(first);
        assertFitsDiscordLimits(second);
        assertThat(selectValues(first, "setup:panel:roles:remove:PRESTIGE:0")).hasSize(25).contains("1", "25");
        assertThat(selectValues(second, "setup:panel:roles:remove:PRESTIGE:1")).containsExactly(
                "26", "27", "28", "29", "30");
        assertThat(customIds(first)).contains("setup:panel:roles:page:PRESTIGE:1");
    }

    @Test
    void leaderboardRemoveMenu_pagesThroughAllBindings() {
        PanelView second = leaderboards.render(guild, 1);

        assertFitsDiscordLimits(second);
        assertThat(selectValues(second, "setup:panel:leaderboards:remove:1"))
                .hasSize(StatisticType.values().length - 25);
    }

    @Test
    void eventTargets_pageThroughAllKnownEvents() {
        PanelView second = event.renderPost(guild, EventSection.CURRENT, 1);

        assertFitsDiscordLimits(second);
        assertThat(selectValues(second, "setup:panel:event:target:1"))
                .startsWith(EventSection.CURRENT).contains("event-25", "event-40").doesNotContain("event-24");
        assertThat(customIds(second)).contains("setup:panel:event:post-page:0:current");
    }

    @Test
    void alertSettings_deletedAlertChannelIsNotPreselected() {
        PanelView view = event.renderAlerts(guild);

        assertThat(entitySelect(view, "setup:panel:event:alert-channel").getDefaultValues()).isEmpty();
    }

    @Test
    void alertSettings_existingAlertChannelIsPreselected() {
        when(guild.getChannelById(StandardGuildMessageChannel.class, "123456789012345678"))
                .thenReturn(mock(StandardGuildMessageChannel.class));

        PanelView view = event.renderAlerts(guild);

        assertThat(entitySelect(view, "setup:panel:event:alert-channel").getDefaultValues()).hasSize(1);
    }

    private static List<String> customIds(PanelView view) {
        List<String> ids = new ArrayList<>();
        for (ActionRow row : view.rows()) {
            for (Component component : row.getComponents()) {
                if (component instanceof ICustomId withId && withId.getCustomId() != null) {
                    ids.add(withId.getCustomId());
                }
            }
        }
        return ids;
    }

    private static List<String> selectValues(PanelView view, String customId) {
        return view.rows().stream().flatMap(row -> row.getComponents().stream())
                .filter(component -> component instanceof StringSelectMenu select
                        && customId.equals(select.getCustomId()))
                .map(StringSelectMenu.class::cast)
                .findFirst().orElseThrow()
                .getOptions().stream().map(SelectOption::getValue).toList();
    }

    private static EntitySelectMenu entitySelect(PanelView view, String customId) {
        return view.rows().stream().flatMap(row -> row.getComponents().stream())
                .filter(component -> component instanceof EntitySelectMenu select
                        && customId.equals(select.getCustomId()))
                .map(EntitySelectMenu.class::cast)
                .findFirst().orElseThrow();
    }

    private static void assertFitsDiscordLimits(PanelView view) {
        view.edit();
        view.create();
        assertThat(view.rows()).hasSizeLessThanOrEqualTo(5);
        Set<String> ids = new HashSet<>();
        for (ActionRow row : view.rows()) {
            for (Component component : row.getComponents()) {
                if (component instanceof ICustomId withId && withId.getCustomId() != null) {
                    assertThat(withId.getCustomId()).hasSizeLessThanOrEqualTo(100);
                    assertThat(ids.add(withId.getCustomId())).as("unique id %s", withId.getCustomId()).isTrue();
                }
                if (component instanceof StringSelectMenu select) {
                    assertThat(select.getOptions()).hasSizeLessThanOrEqualTo(25);
                }
            }
        }
    }
}
