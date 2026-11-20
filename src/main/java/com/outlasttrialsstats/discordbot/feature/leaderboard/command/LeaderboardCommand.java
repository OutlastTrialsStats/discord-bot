package com.outlasttrialsstats.discordbot.feature.leaderboard.command;

import com.outlasttrialsstats.backend.api.model.DiscordLeaderboardResponse;
import com.outlasttrialsstats.backend.api.model.StatisticType;
import com.outlasttrialsstats.discordbot.entity.GuildServer;
import com.outlasttrialsstats.discordbot.feature.leaderboard.LeaderboardCategories;
import com.outlasttrialsstats.discordbot.feature.leaderboard.service.LeaderboardService;
import com.outlasttrialsstats.discordbot.repository.GuildServerRepository;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LeaderboardCommand {

    private final LeaderboardService leaderboardService;
    private final MessageService messageService;
    private final GuildServerRepository guildServerRepository;

    public void onLeaderboard(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Optional<StatisticType> parsed = LeaderboardCategories.parse(event.getOption("category").getAsString());
        if (parsed.isEmpty()) {
            event.reply(messageService.getMessage(guildId, "leaderboard.unknown_category")).setEphemeral(true).queue();
            return;
        }
        StatisticType category = parsed.get();
        boolean ephemeral = !isPublic(guildId);

        event.deferReply(ephemeral).queue();
        var hook = event.getHook();

        Optional<DiscordLeaderboardResponse> response = leaderboardService.fetchLeaderboard(category, 1);
        if (response.isEmpty()) {
            hook.editOriginal(messageService.getMessage(guildId, "leaderboard.error")).queue();
            return;
        }

        DiscordLeaderboardResponse data = response.get();
        int totalPages = data.getTotalPages() != null ? data.getTotalPages() : 1;
        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(guildId, event.getGuild(), category, data, true, true, true);

        var reply = hook.editOriginalEmbeds(embed);
        List<Button> buttons = buildButtons(category.getValue(), 1, totalPages);
        if (!buttons.isEmpty()) {
            reply.setComponents(ActionRow.of(buttons));
        }
        reply.queue();
    }

    /** Suggests categories for the "category" option of /leaderboard and /setup leaderboard. */
    public void onCategoryAutoComplete(CommandAutoCompleteInteractionEvent event) {
        Guild guild = event.getGuild();
        event.replyChoices(LeaderboardCategories.suggest(event.getFocusedOption().getValue(),
                type -> guild != null ? leaderboardService.getCategoryDisplayName(guild.getId(), type) : type.getValue()))
                .queue();
    }

    private boolean isPublic(String guildId) {
        return guildServerRepository.findById(guildId)
                .map(GuildServer::isUserCommandsVisible)
                .orElse(false);
    }

    public void onButton(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        if (parts.length != 4) return;

        Optional<StatisticType> parsed = LeaderboardCategories.parse(parts[2]);
        if (parsed.isEmpty()) return;
        StatisticType category = parsed.get();
        int targetPage = Integer.parseInt(parts[3]);
        String guildId = event.getGuild().getId();
        Guild guild = event.getJDA().getGuildById(guildId);

        event.deferEdit().queue();
        var hook = event.getHook();

        Optional<DiscordLeaderboardResponse> response = leaderboardService.fetchLeaderboard(category, targetPage);
        if (response.isEmpty()) {
            hook.sendMessage(messageService.getMessage(guildId, "leaderboard.error"))
                    .setEphemeral(true).queue();
            return;
        }

        DiscordLeaderboardResponse data = response.get();
        int totalPages = data.getTotalPages() != null ? data.getTotalPages() : 1;
        MessageEmbed embed = leaderboardService.buildLeaderboardEmbed(guildId, guild, category, data, true, true, true);

        List<Button> buttons = buildButtons(category.getValue(), targetPage, totalPages);
        var edit = hook.editOriginalEmbeds(embed);
        if (!buttons.isEmpty()) {
            edit.setComponents(ActionRow.of(buttons));
        } else {
            edit.setComponents();
        }
        edit.queue();
    }

    private List<Button> buildButtons(String categoryValue, int currentPage, int totalPages) {
        List<Button> buttons = new ArrayList<>();
        if (currentPage > 1) {
            buttons.add(Button.of(ButtonStyle.SECONDARY,
                    "leaderboard:prev:" + categoryValue + ":" + (currentPage - 1), "Previous"));
        }
        if (currentPage < totalPages) {
            buttons.add(Button.of(ButtonStyle.SECONDARY,
                    "leaderboard:next:" + categoryValue + ":" + (currentPage + 1), "Next"));
        }
        return buttons;
    }
}
