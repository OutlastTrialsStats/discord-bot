package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.service.BasicSetupService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StartBasicCommand {

    private final BasicSetupService basicSetupService;
    private final MessageService messageService;

    public void onStartBasic(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        StringSelectMenu menu = StringSelectMenu.create("setup:start:categories")
                .setPlaceholder("Select role categories")
                .setRequiredRange(1, 7)
                .addOption("Prestige Roles", "prestige", "Roles based on prestige level (1+, 10+, 20+, ...)")
                .addOption("Level Roles", "level", "Roles based on player level (1+, 10+, 25+, ...)")
                .addOption("Reagent Rig Roles", "reagent_rig", "Roles based on active reagent rig (Stun, X-Ray, ...)")
                .addOption("Invasion Ranking Roles", "invasion_ranking", "Roles based on invasion rank (Bronze, Silver, Gold, ...)")
                .addOption("Total Invasion Matches Roles", "total_invasion_matches", "Roles based on total invasion matches played (10+, 50+, 100+, ...)")
                .addOption("Platform Roles", "platform", "Roles based on gaming platform (Steam, PlayStation, ...)")
                .addOption("Account Type Roles", "account_type", "Roles based on account type (Closed Beta, Early Access, ...)")
                .build();

        event.reply(messageService.getMessage(guildId, "setup.start.select_prompt"))
                .setEphemeral(true)
                .addComponents(ActionRow.of(menu))
                .queue();
    }

    public void onCategorySelect(StringSelectInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        List<String> values = event.getValues();

        Set<RoleCategory> selectedCategories = EnumSet.noneOf(RoleCategory.class);
        for (String value : values) {
            RoleCategory category = RoleCategory.fromId(value);
            if (category != null) {
                selectedCategories.add(category);
            }
        }

        event.editMessage(messageService.getMessage(guildId, "setup.basic.starting"))
                .setComponents()
                .queue();

        InteractionHook hook = event.getHook();

        basicSetupService.setupRoles(guild, selectedCategories,
                (count, roles) -> hook.editOriginal(
                        messageService.getMessage(guildId, "setup.basic.completed", count, String.join(", ", roles))
                ).queue(),
                () -> hook.editOriginal(
                        messageService.getMessage(guildId, "setup.basic.no_changes")
                ).queue()
        );
    }
}
