package com.outlasttrialsstats.discordbot.feature.setup.command;

import com.outlasttrialsstats.discordbot.feature.setup.RoleCategory;
import com.outlasttrialsstats.discordbot.feature.setup.service.BasicSetupService;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import io.github.kaktushose.jdac.annotations.interactions.Command;
import io.github.kaktushose.jdac.annotations.interactions.CommandConfig;
import io.github.kaktushose.jdac.annotations.interactions.Interaction;
import io.github.kaktushose.jdac.annotations.interactions.MenuOption;
import io.github.kaktushose.jdac.annotations.interactions.StringSelectMenu;
import io.github.kaktushose.jdac.dispatching.events.interactions.CommandEvent;
import io.github.kaktushose.jdac.dispatching.events.interactions.ComponentEvent;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.stereotype.Component;

@Interaction
@Component
@RequiredArgsConstructor
public class StartBasicCommand {

    private final BasicSetupService basicSetupService;
    private final MessageService messageService;

    @CommandConfig(enabledFor = Permission.MANAGE_ROLES)
    @Command("setup start")
    public void onStartBasic(CommandEvent event) {
        String guildId = event.getGuild().getId();
        event.with()
                .ephemeral(true)
                .components("onCategorySelect")
                .reply(messageService.getMessage(guildId, "setup.start.select_prompt"));
    }

    @StringSelectMenu(value = "Select role categories", minValue = 1, maxValue = 5)
    @MenuOption(label = "Prestige Roles", value = "prestige", description = "Roles based on prestige level (1+, 10+, 20+, ...)")
    @MenuOption(label = "Reagent Rig Roles", value = "reagent_rig", description = "Roles based on active reagent rig (Stun, X-Ray, ...)")
    @MenuOption(label = "Invasion Ranking Roles", value = "invasion_ranking", description = "Roles based on invasion rank (Bronze, Silver, Gold, ...)")
    @MenuOption(label = "Platform Roles", value = "platform", description = "Roles based on gaming platform (Steam, PlayStation, ...)")
    @MenuOption(label = "Account Type Roles", value = "account_type", description = "Roles based on account type (Closed Beta, Early Access, ...)")
    public void onCategorySelect(ComponentEvent event, List<String> values) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();

        Set<RoleCategory> selectedCategories = EnumSet.noneOf(RoleCategory.class);
        for (String value : values) {
            RoleCategory category = RoleCategory.fromId(value);
            if (category != null) {
                selectedCategories.add(category);
            }
        }

        event.with().editReply(true).keepComponents(false)
                .reply(messageService.getMessage(guildId, "setup.basic.starting"));

        InteractionHook hook = event.jdaEvent().getHook();

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
