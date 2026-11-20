package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEventMessagePublisher;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventService;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventSettings;
import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.service.BasicSetupService;
import com.outlasttrialsstats.discordbot.feature.setup.service.GuildSettingsService;
import com.outlasttrialsstats.discordbot.shared.BotChannels;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StartBasicCommand {

    public static final String COMPONENT_PREFIX = "setup:start:";
    public static final String CATEGORIES = COMPONENT_PREFIX + "categories";
    public static final String EVENT_CHANNEL = COMPONENT_PREFIX + "event-channel";
    static final String SKIP_ROLES = COMPONENT_PREFIX + "skip-roles";
    static final String NICKNAME_ON = COMPONENT_PREFIX + "nickname-on";
    static final String NICKNAME_SKIP = COMPONENT_PREFIX + "nickname-skip";
    static final String EVENT_SKIP = COMPONENT_PREFIX + "event-skip";

    private final BasicSetupService basicSetupService;
    private final MessageService messageService;
    private final GuildSettingsService settingsService;
    private final CommunityEventMessagePublisher eventPublisher;
    private final CommunityEventService eventService;
    private final CommunityEventSettings eventSettings;

    public void start(IReplyCallback event) {
        String guildId = event.getGuild().getId();

        StringSelectMenu menu = StringSelectMenu.create(CATEGORIES)
                .setPlaceholder("Select role categories")
                .setRequiredRange(1, 9)
                .addOption("Prestige Roles", "prestige", "Roles based on prestige level (1+, 10+, 20+, ...)")
                .addOption("Level Roles", "level", "Roles based on player level (1+, 10+, 25+, ...)")
                .addOption("Reagent Rig Roles", "reagent_rig", "Roles based on active reagent rig (Stun, X-Ray, ...)")
                .addOption("Invasion Ranking Roles", "invasion_ranking", "Roles based on invasion rank (Bronze, Silver, Gold, ...)")
                .addOption("Total Invasion Matches Roles", "total_invasion_matches", "Roles based on total invasion matches played (10+, 50+, 100+, ...)")
                .addOption("Season Invasion Points Roles", "season_invasion_points", "Roles based on season invasion points (100+, 500+, 1000+, ...)")
                .addOption("Platform Roles", "platform", "Roles based on gaming platform (Steam, PlayStation, ...)")
                .addOption("Account Type Roles", "account_type", "Roles based on account type (Closed Beta, Early Access, ...)")
                .addOption("Connected Account Role", "connected_account", "Role granted to members with a linked Outlast Trials Stats account")
                .build();

        event.reply(message(guildId, "setup.start.select_prompt"))
                .setEphemeral(true)
                .setComponents(ActionRow.of(menu),
                        ActionRow.of(Button.secondary(SKIP_ROLES, message(guildId, "setup.start.skip"))))
                .queue();
    }

    public void onCategorySelect(StringSelectInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);

        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            event.editMessage(message(guildId, "error.missing_permission.manage_roles"))
                    .setComponents()
                    .queue();
            return;
        }

        Set<RoleCategory> selectedCategories = EnumSet.noneOf(RoleCategory.class);
        for (String value : event.getValues()) {
            RoleCategory category = RoleCategory.fromId(value);
            if (category != null) {
                selectedCategories.add(category);
            }
        }

        event.editMessage(message(guildId, "setup.basic.starting"))
                .setComponents()
                .queue();

        InteractionHook hook = event.getHook();

        basicSetupService.setupRoles(guild, selectedCategories,
                result -> showNicknameStep(hook, guildId, buildResultMessage(guildId, result)),
                () -> showNicknameStep(hook, guildId, message(guildId, "setup.basic.no_changes"))
        );
    }

    public void onButton(ButtonInteractionEvent event) {
        String guildId = event.getGuild().getId();
        switch (event.getComponentId()) {
            case SKIP_ROLES -> event.editMessage(nicknamePrompt(guildId, null))
                    .setComponents(nicknameRow(guildId)).queue();
            case NICKNAME_ON -> {
                String result = settingsService.setAutoNickname(event.getGuild(), true);
                event.editMessage(eventPrompt(guildId, result)).setComponents(eventRows(guildId)).queue();
            }
            case NICKNAME_SKIP -> event.editMessage(eventPrompt(guildId, null))
                    .setComponents(eventRows(guildId)).queue();
            case EVENT_SKIP -> event.editMessage(message(guildId, "setup.start.done")).setComponents().queue();
            default -> {}
        }
    }

    public void onEventChannelSelect(EntitySelectInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        List<GuildChannel> channels = event.getMentions().getChannels();
        if (channels.isEmpty() || !(channels.getFirst() instanceof StandardGuildMessageChannel channel)) {
            event.editMessage(eventPrompt(guildId, message(guildId, "error.invalid_channel_type")))
                    .setComponents(eventRows(guildId)).queue();
            return;
        }
        if (!guild.getSelfMember().hasPermission(channel, BotChannels.SEND_AND_EDIT_EMBEDS)) {
            event.editMessage(eventPrompt(guildId,
                            message(guildId, "error.missing_permission.channel_write", channel.getAsMention())))
                    .setComponents(eventRows(guildId)).queue();
            return;
        }

        event.deferEdit().queue();
        InteractionHook hook = event.getHook();

        CommunityEventMessagePublisher.Outcome outcome = eventPublisher.publish(guild, channel, null);
        eventService.setAlertChannel(guildId, channel.getId());

        List<String> lines = new ArrayList<>();
        lines.add(outcome instanceof CommunityEventMessagePublisher.Outcome.NoCurrentEvent
                ? message(guildId, "setup.start.event_no_current")
                : eventSettings.describe(guildId, outcome, channel));
        lines.add(message(guildId, "setup.start.event_alerts", channel.getAsMention()));
        lines.add("");
        lines.add(message(guildId, "setup.start.done"));
        hook.editOriginal(String.join("\n", lines)).setComponents().queue();
    }

    String buildResultMessage(String guildId, BasicSetupService.SetupResult result) {
        List<String> lines = new ArrayList<>();
        if (!result.createdRoles().isEmpty()) {
            lines.add(message(guildId, "setup.basic.completed",
                    result.createdRoles().size(), String.join(", ", result.createdRoles())));
        }
        if (!result.failedRoles().isEmpty()) {
            lines.add(message(guildId, "setup.basic.failed",
                    result.failedRoles().size(), String.join(", ", result.failedRoles())));
        }
        return String.join("\n", lines);
    }

    private void showNicknameStep(InteractionHook hook, String guildId, String roleResult) {
        hook.editOriginal(nicknamePrompt(guildId, roleResult)).setComponents(nicknameRow(guildId)).queue();
    }

    private String nicknamePrompt(String guildId, String previousResult) {
        return withPrevious(previousResult, message(guildId, "setup.start.nickname_prompt"));
    }

    private String eventPrompt(String guildId, String previousResult) {
        return withPrevious(previousResult, message(guildId, "setup.start.event_prompt"));
    }

    private static String withPrevious(String previousResult, String prompt) {
        return previousResult == null || previousResult.isBlank() ? prompt : previousResult + "\n\n" + prompt;
    }

    private ActionRow nicknameRow(String guildId) {
        return ActionRow.of(
                Button.primary(NICKNAME_ON, message(guildId, "setup.start.nickname_enable")),
                Button.secondary(NICKNAME_SKIP, message(guildId, "setup.start.skip")));
    }

    private List<ActionRow> eventRows(String guildId) {
        EntitySelectMenu channelMenu = EntitySelectMenu.create(EVENT_CHANNEL, EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)
                .setPlaceholder(message(guildId, "setup.start.event_placeholder"))
                .build();
        return List.of(ActionRow.of(channelMenu),
                ActionRow.of(Button.secondary(EVENT_SKIP, message(guildId, "setup.start.skip"))));
    }

    private String message(String guildId, String key, Object... args) {
        return messageService.getMessage(guildId, key, args);
    }
}
