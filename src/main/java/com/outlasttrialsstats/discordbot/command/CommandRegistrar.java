package com.outlasttrialsstats.discordbot.command;

import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.shared.EnumFormatter;
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
                                .addChoices(leaderboardChoices())),

                Commands.slash("sync-profile", "Sync your roles based on your Outlast Trials stats")
                        .setContexts(InteractionContextType.GUILD),

                Commands.slash("widget", "Show your Outlast Trials stats on your Discord profile")
                        .setContexts(InteractionContextType.GUILD)
                        .addSubcommands(
                                new SubcommandData("enable", "Authorize and enable the profile widget"),
                                new SubcommandData("disable", "Disable the widget and clear its data"),
                                new SubcommandData("status", "Show your widget status"),
                                new SubcommandData("refresh", "Push your latest stats to the widget now")),

                Commands.slash("sync-all", "Sync roles for all members in this server")
                        .setContexts(InteractionContextType.GUILD)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES)),

                Commands.slash("setup", "Bot setup commands")
                        .setContexts(InteractionContextType.GUILD)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
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
                                                        .addChoices(leaderboardChoices()),
                                                new OptionData(OptionType.INTEGER, "pages", "Number of pages to display (1-10)", true)),
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
                                buildRemoveRoleMappingGroup()
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

    private static OptionData thresholdOption(String description) {
        return new OptionData(OptionType.INTEGER, "threshold", description, true);
    }

    private static OptionData stringOption(String name, String description, List<Choice> choices) {
        return new OptionData(OptionType.STRING, name, description, true).addChoices(choices);
    }

    static List<Choice> leaderboardChoices() {
        return Arrays.stream(StatisticType.values())
                .map(t -> new Choice(EnumFormatter.titleCase(t.getValue()), t.getValue()))
                .toList();
    }

    private static List<Choice> choices(String... values) {
        return Arrays.stream(values)
                .map(v -> new Choice(v, v))
                .toList();
    }
}
