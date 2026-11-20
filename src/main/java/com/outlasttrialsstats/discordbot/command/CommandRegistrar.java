package com.outlasttrialsstats.discordbot.command;

import com.outlasttrialsstats.discordbot.feature.communityevent.command.CommunityEventSetupCommand;
import com.outlasttrialsstats.discordbot.entity.CommunityAlertType;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommandRegistrar {

    private static final List<Choice> SKILL_CHOICES = choices("STUN", "XRAY", "MINE", "DOOR_BLOCKER", "HACKER", "HEAL");
    private static final List<Choice> INVASION_RANKING_CHOICES = choices(
            "UNRANKED", "INITIATE_3", "INITIATE_2", "INITIATE_1",
            "BRONZE_3", "BRONZE_2", "BRONZE_1",
            "SILVER_3", "SILVER_2", "SILVER_1",
            "GOLD_3", "GOLD_2", "GOLD_1");
    private static final List<Choice> PLATFORM_CHOICES = choices("STEAM", "PLAYSTATION", "XBOX", "EPIC_GAMES");
    private static final List<Choice> ACCOUNT_TYPE_CHOICES = choices("CLOSED_BETA_USER", "EARLY_ACCESS_USER");

    private static final OptionData OPTIONAL_ROLE = new OptionData(OptionType.ROLE, "role",
            "Existing role to use (auto-creates if not provided)", false);

    private final JDA jda;

    @EventListener(ApplicationReadyEvent.class)
    public void registerCommands() {
        jda.updateCommands().addCommands(
                Commands.slash("leaderboard", "View the leaderboard for a stat category")
                        .addOptions(new OptionData(OptionType.STRING, "category", "Statistic category", true)
                                .setAutoComplete(true)),

                Commands.slash("sync-profile", "Sync your roles based on your Outlast Trials stats")
                        .setContexts(InteractionContextType.GUILD),

                Commands.slash("sync-all", "Sync roles for all members in this server")
                        .setContexts(InteractionContextType.GUILD)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES)),

                Commands.slash("setup", "Bot setup commands")
                        .setContexts(InteractionContextType.GUILD)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                        .addSubcommands(
                                new SubcommandData("delete", "Delete all bot-managed roles from this server"),
                                new SubcommandData("language", "Set the bot language for this server")
                                        .addOptions(new OptionData(OptionType.STRING, "language", "Language", true)
                                                .addChoices(new Choice("English", "en"), new Choice("Deutsch", "de"))),
                                new SubcommandData("leaderboard", "Set up an auto-updating leaderboard in a channel")
                                        .addOptions(
                                                new OptionData(OptionType.CHANNEL, "channel", "Channel to post the leaderboard in", true)
                                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS),
                                                new OptionData(OptionType.STRING, "category", "Statistic category", true)
                                                        .setAutoComplete(true),
                                                new OptionData(OptionType.INTEGER, "pages", "Number of pages to display (1-10)", true)
                                                        .setRequiredRange(1, 10)),
                                new SubcommandData("messages-upload", "Upload custom bot messages from a properties file")
                                        .addOptions(new OptionData(OptionType.ATTACHMENT, "file", "Properties file with custom messages", true)),
                                new SubcommandData("messages-download", "Download current bot messages as a properties file"),
                                new SubcommandData("messages-reset", "Reset all custom messages to defaults"),
                                new SubcommandData("start", "Start the basic role setup wizard"),
                                new SubcommandData("nickname", "Toggle automatic nickname sync for verified users")
                                        .addOptions(new OptionData(OptionType.BOOLEAN, "enabled", "Enable or disable auto-nickname", true)),
                                new SubcommandData("toggle-user-command-visibility", "Toggle whether user command responses are visible to everyone")
                                        .addOptions(new OptionData(OptionType.BOOLEAN, "enabled", "Enable public user command responses", true))
                        )
                        .addSubcommandGroups(
                                buildRoleMappingGroup(),
                                buildRemoveRoleMappingGroup(),
                                buildCommunityEventGroup()
                        )
        ).queue();
    }

    private static SubcommandGroupData buildRoleMappingGroup() {
        return new SubcommandGroupData("role-mapping", "Map roles to stat categories")
                .addSubcommands(
                        new SubcommandData("prestige", "Map a role to a minimum prestige level")
                                .addOptions(thresholdOption("Minimum prestige level"), OPTIONAL_ROLE),
                        new SubcommandData("level", "Map a role to a minimum player level")
                                .addOptions(thresholdOption("Minimum level"), OPTIONAL_ROLE),
                        new SubcommandData("skill", "Map a role to a reagent rig skill")
                                .addOptions(stringOption("skill", "Reagent skill", SKILL_CHOICES), OPTIONAL_ROLE),
                        new SubcommandData("invasion-ranking", "Map a role to an invasion ranking")
                                .addOptions(stringOption("ranking", "Invasion ranking", INVASION_RANKING_CHOICES), OPTIONAL_ROLE),
                        new SubcommandData("total-invasion-matches", "Map a role to a minimum number of invasion matches")
                                .addOptions(thresholdOption("Minimum total invasion matches"), OPTIONAL_ROLE),
                        new SubcommandData("season-invasion-points", "Map a role to a minimum number of season invasion points")
                                .addOptions(thresholdOption("Minimum season invasion points"), OPTIONAL_ROLE),
                        new SubcommandData("platform", "Map a role to a gaming platform")
                                .addOptions(stringOption("platform", "Gaming platform", PLATFORM_CHOICES), OPTIONAL_ROLE),
                        new SubcommandData("account-type", "Map a role to an account type")
                                .addOptions(stringOption("account-type", "Account type", ACCOUNT_TYPE_CHOICES), OPTIONAL_ROLE),
                        new SubcommandData("connected-account", "Map a role granted to members with a linked TotStats account")
                                .addOptions(OPTIONAL_ROLE)
                );
    }

    private static SubcommandGroupData buildRemoveRoleMappingGroup() {
        return new SubcommandGroupData("remove-role-mapping", "Remove role mappings")
                .addSubcommands(
                        new SubcommandData("prestige", "Remove a prestige role mapping")
                                .addOptions(thresholdOption("Prestige threshold to remove")),
                        new SubcommandData("level", "Remove a level role mapping")
                                .addOptions(thresholdOption("Level threshold to remove")),
                        new SubcommandData("skill", "Remove a reagent rig skill role mapping")
                                .addOptions(stringOption("skill", "Reagent skill", SKILL_CHOICES)),
                        new SubcommandData("invasion-ranking", "Remove an invasion ranking role mapping")
                                .addOptions(stringOption("ranking", "Invasion ranking", INVASION_RANKING_CHOICES)),
                        new SubcommandData("total-invasion-matches", "Remove a total invasion matches role mapping")
                                .addOptions(thresholdOption("Total invasion matches threshold to remove")),
                        new SubcommandData("season-invasion-points", "Remove a season invasion points role mapping")
                                .addOptions(thresholdOption("Season invasion points threshold to remove")),
                        new SubcommandData("platform", "Remove a platform role mapping")
                                .addOptions(stringOption("platform", "Gaming platform", PLATFORM_CHOICES)),
                        new SubcommandData("account-type", "Remove an account type role mapping")
                                .addOptions(stringOption("account-type", "Account type", ACCOUNT_TYPE_CHOICES)),
                        new SubcommandData("connected-account", "Remove the connected-account role mapping")
                );
    }

    static SubcommandGroupData buildCommunityEventGroup() {
        return new SubcommandGroupData(CommunityEventSetupCommand.GROUP, "Community event progress message and alerts")
                .addSubcommands(
                        new SubcommandData("message", "Post an auto-updating community event progress message")
                                .addOptions(
                                        new OptionData(OptionType.CHANNEL, "channel", "Channel to post the progress in", true)
                                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS),
                                        new OptionData(OptionType.STRING, "event",
                                                "Event to show (leave empty to always show the current event)", false)
                                                .setAutoComplete(true)),
                        new SubcommandData("alerts", "Set the channel community event alerts are posted in")
                                .addOptions(new OptionData(OptionType.CHANNEL, "channel", "Channel for the alerts", true)
                                        .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)),
                        new SubcommandData("alert", "Configure one alert type: on/off, ping role and lead time")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "type", "Alert type", true)
                                                .addChoices(
                                                        new Choice("Phase reached", CommunityAlertType.MILESTONE_REACHED.name()),
                                                        new Choice("Pre-alert (phase almost reached)", CommunityAlertType.PRE_ALERT.name())),
                                        new OptionData(OptionType.BOOLEAN, "enabled", "Turn this alert on or off", false),
                                        new OptionData(OptionType.ROLE, "role", "Role to ping with this alert", false),
                                        new OptionData(OptionType.BOOLEAN, "remove-role", "Post this alert without a ping", false),
                                        new OptionData(OptionType.INTEGER, "lead-time", "Pre-alert only: how early to warn", false)
                                                .addChoices(new Choice("15 minutes", 15), new Choice("1 hour", 60),
                                                        new Choice("3 hours", 180))),
                        new SubcommandData("status", "Show the community event settings of this server"),
                        new SubcommandData("remove", "Remove the progress message and/or turn off alerts")
                                .addOptions(new OptionData(OptionType.STRING, "target", "What to remove", true)
                                        .addChoices(new Choice("Progress message", "message"),
                                                new Choice("Alerts", "alerts"), new Choice("Both", "all")))
                );
    }

    private static OptionData thresholdOption(String description) {
        return new OptionData(OptionType.INTEGER, "threshold", description, true)
                .setMinValue(1);
    }

    private static OptionData stringOption(String name, String description, List<Choice> choices) {
        return new OptionData(OptionType.STRING, name, description, true).addChoices(choices);
    }

    private static List<Choice> choices(String... values) {
        return Arrays.stream(values)
                .map(v -> new Choice(v, v))
                .toList();
    }
}
