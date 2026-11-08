package com.outlasttrialsstats.discordbot.feature.communityevent.command;

import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import com.outlasttrialsstats.discordbot.entity.CommunityAlertType;
import com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEventEmbedFactory;
import com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEventScheduler;
import com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEvents;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.AlertRule;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventBinding;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventService;
import com.outlasttrialsstats.discordbot.feature.setup.RoleSafety;
import com.outlasttrialsstats.discordbot.shared.BotChannels;
import com.outlasttrialsstats.discordbot.shared.MessageCleanup;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommunityEventSetupCommand {

    public static final String GROUP = "community-event";
    private static final int MAX_AUTOCOMPLETE_CHOICES = 25;
    private static final int MAX_CHOICE_NAME_LENGTH = 100;

    private final CommunityEventService service;
    private final CommunityEventEmbedFactory embedFactory;
    private final CommunityEventScheduler scheduler;
    private final MessageService messageService;

    public void onSubcommand(SlashCommandInteractionEvent event, String subcommand) {
        switch (subcommand) {
            case "message" -> onMessage(event);
            case "alerts" -> onAlerts(event);
            case "alert" -> onAlert(event);
            case "status" -> onStatus(event);
            case "remove" -> onRemove(event);
            default -> {}
        }
    }

    void onMessage(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        Optional<StandardGuildMessageChannel> target = BotChannels.asTextOrNews(event.getOption("channel").getAsChannel());
        if (target.isEmpty()) {
            hook.editOriginal(message(guildId, "error.invalid_channel_type")).queue();
            return;
        }
        StandardGuildMessageChannel channel = target.get();
        if (!guild.getSelfMember().hasPermission(channel, BotChannels.SEND_AND_EDIT_EMBEDS)) {
            hook.editOriginal(message(guildId, "error.missing_permission.channel_write", channel.getAsMention()))
                    .queue();
            return;
        }

        Optional<List<DiscordCommunityEvent>> events = service.getEvents();
        if (events.isEmpty()) {
            hook.editOriginal(message(guildId, "community_event.error")).queue();
            return;
        }

        Instant now = Instant.now();
        String eventId = Optional.ofNullable(event.getOption("event", null, OptionMapping::getAsString))
                .map(String::trim).filter(id -> !id.isEmpty()).orElse(null);
        Optional<DiscordCommunityEvent> shown = eventId == null
                ? CommunityEvents.selectCurrent(events.get(), now)
                : CommunityEvents.find(events.get(), eventId);
        if (shown.isEmpty()) {
            hook.editOriginal(eventId == null
                    ? message(guildId, "setup.community_event.no_event")
                    : message(guildId, "setup.community_event.unknown_event", eventId)).queue();
            return;
        }

        Message posted = null;
        Optional<CommunityEventBinding> previous;
        try {
            posted = channel.sendMessageEmbeds(embedFactory.buildProgressEmbed(guildId, shown.get(), now))
                    .setComponents(embedFactory.buildButtons(guildId, shown.get()))
                    .complete();
            previous = service.saveBinding(guildId, eventId, shown.get().getId(), channel.getId(), posted.getId());
        } catch (Exception e) {
            log.warn("Failed to set up community event message in guild {}: {}", guildId, e.getMessage());
            if (posted != null) {
                posted.delete().queue(_ -> {}, _ -> {});
            }
            hook.editOriginal(message(guildId, "setup.community_event.message.post_failed", channel.getAsMention()))
                    .queue();
            return;
        }
        scheduler.forget(guildId);
        previous.ifPresent(binding -> deleteMessage(guild, binding));

        hook.editOriginal(message(guildId, eventId == null
                        ? "setup.community_event.message.success_auto"
                        : "setup.community_event.message.success",
                shown.get().getName(), channel.getAsMention())).queue();
    }

    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if (!"event".equals(event.getFocusedOption().getName())) {
            return;
        }
        String typed = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        List<Choice> choices = service.getCachedEvents().stream()
                .filter(e -> e.getId().toLowerCase(Locale.ROOT).contains(typed)
                        || e.getName().toLowerCase(Locale.ROOT).contains(typed))
                .limit(MAX_AUTOCOMPLETE_CHOICES)
                .map(e -> new Choice(choiceName(e), e.getId()))
                .toList();
        event.replyChoices(choices).queue();
    }

    private static String choiceName(DiscordCommunityEvent event) {
        String name = event.getName() + " (" + event.getId() + ")";
        return name.length() <= MAX_CHOICE_NAME_LENGTH ? name : name.substring(0, MAX_CHOICE_NAME_LENGTH - 1) + "…";
    }

    void onAlerts(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        Optional<StandardGuildMessageChannel> channel = BotChannels.asTextOrNews(event.getOption("channel").getAsChannel());
        if (channel.isEmpty()) {
            event.reply(message(guildId, "error.invalid_channel_type")).setEphemeral(true).queue();
            return;
        }
        if (!guild.getSelfMember().hasPermission(channel.get(), BotChannels.SEND_EMBEDS)) {
            event.reply(message(guildId, "error.missing_permission.alert_channel", channel.get().getAsMention()))
                    .setEphemeral(true).queue();
            return;
        }
        service.setAlertChannel(guildId, channel.get().getId());
        event.reply(message(guildId, "setup.community_event.alerts.success", channel.get().getAsMention()))
                .addEmbeds(statusEmbed(guild))
                .setEphemeral(true).queue();
    }

    void onAlert(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        CommunityAlertType type = CommunityAlertType.valueOf(event.getOption("type").getAsString());
        Boolean enabled = event.getOption("enabled", null, OptionMapping::getAsBoolean);
        Role role = event.getOption("role", null, OptionMapping::getAsRole);
        boolean removeRole = event.getOption("remove-role", false, OptionMapping::getAsBoolean);
        Integer leadTime = event.getOption("lead-time", null, OptionMapping::getAsInt);

        if (role != null && role.isManaged()) {
            event.reply(message(guildId, RoleSafety.Violation.MANAGED_ROLE.getMessageKey(), role.getName()))
                    .setEphemeral(true).queue();
            return;
        }

        service.updateRule(guildId, type, enabled, role == null ? null : role.getId(), removeRole, leadTime);

        List<String> notes = new ArrayList<>();
        notes.add(message(guildId, "setup.community_event.alert.updated", typeName(guildId, type)));
        if (leadTime != null && !type.hasLeadTime()) {
            notes.add(message(guildId, "setup.community_event.alert.lead_time_ignored"));
        }
        Optional<String> alertChannelId = service.getAlertChannelId(guildId);
        if (alertChannelId.isEmpty()) {
            notes.add(message(guildId, "setup.community_event.alert.no_channel"));
        } else if (role != null && !removeRole) {
            StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class,
                    alertChannelId.get());
            if (channel != null && !canPing(guild, channel, role)) {
                notes.add(message(guildId, "setup.community_event.alert.role_not_pingable",
                        roleMention(role), channel.getAsMention()));
            }
        }

        event.reply(String.join("\n", notes))
                .addEmbeds(statusEmbed(guild))
                .setAllowedMentions(List.of())
                .setEphemeral(true).queue();
    }

    static boolean canPing(Guild guild, StandardGuildMessageChannel channel, Role role) {
        if (!role.isPublicRole() && role.isMentionable()) {
            return true;
        }
        return guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_MENTION_EVERYONE);
    }

    void onStatus(SlashCommandInteractionEvent event) {
        event.replyEmbeds(statusEmbed(event.getGuild())).setEphemeral(true).queue();
    }

    MessageEmbed statusEmbed(Guild guild) {
        String guildId = guild.getId();
        List<String> lines = new ArrayList<>();

        lines.add(service.getBinding(guildId)
                .map(binding -> message(guildId, "setup.community_event.status.message",
                        "<#" + binding.channelId() + ">", binding.followsCurrentEvent()
                                ? message(guildId, "setup.community_event.status.message_auto")
                                : binding.eventId()))
                .orElseGet(() -> message(guildId, "setup.community_event.status.message_none")));
        lines.add(service.getAlertChannelId(guildId)
                .map(id -> message(guildId, "setup.community_event.status.alert_channel", "<#" + id + ">"))
                .orElseGet(() -> message(guildId, "setup.community_event.status.alert_channel_none")));
        lines.add("");

        Map<CommunityAlertType, AlertRule> rules = service.getRules(guildId);
        for (CommunityAlertType type : CommunityAlertType.values()) {
            lines.add(ruleLine(guild, rules.get(type)));
        }

        return new EmbedBuilder()
                .setColor(CommunityEventEmbedFactory.COLOR)
                .setTitle(message(guildId, "setup.community_event.status.title"))
                .setDescription(String.join("\n", lines))
                .build();
    }

    private String ruleLine(Guild guild, AlertRule rule) {
        String guildId = guild.getId();
        StringBuilder line = new StringBuilder()
                .append(rule.enabled() ? "✅ " : "❌ ")
                .append("**").append(typeName(guildId, rule.type())).append("**");
        if (!rule.enabled()) {
            return line.toString();
        }
        Role role = rule.roleId() == null ? null : guild.getRoleById(rule.roleId());
        line.append(role == null
                ? message(guildId, "setup.community_event.status.rule_no_role")
                : message(guildId, "setup.community_event.status.rule_role", roleMention(role)));
        if (rule.type().hasLeadTime()) {
            line.append(message(guildId, "setup.community_event.status.rule_lead_time",
                    formatLeadTime(rule.leadTimeMinutes())));
        }
        return line.toString();
    }

    static String formatLeadTime(int minutes) {
        return minutes % 60 == 0 ? (minutes / 60) + " h" : minutes + " min";
    }

    void onRemove(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        String target = event.getOption("target").getAsString();
        List<String> lines = new ArrayList<>();

        if ("message".equals(target) || "all".equals(target)) {
            service.removeBinding(guildId).ifPresent(binding -> {
                deleteMessage(guild, binding);
                scheduler.forget(guildId);
                lines.add(message(guildId, "setup.community_event.remove.message"));
            });
        }
        if (("alerts".equals(target) || "all".equals(target)) && service.removeAlertChannel(guildId)) {
            lines.add(message(guildId, "setup.community_event.remove.alerts"));
        }
        if (lines.isEmpty()) {
            lines.add(message(guildId, "setup.community_event.remove.nothing"));
        }
        event.reply(String.join("\n", lines)).setEphemeral(true).queue();
    }

    private static void deleteMessage(Guild guild, CommunityEventBinding binding) {
        StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class,
                binding.channelId());
        if (channel != null) {
            MessageCleanup.deleteMessages(channel, List.of(binding.messageId()), Set.of());
        }
    }

    private static String roleMention(Role role) {
        return role.isPublicRole() ? "@everyone" : role.getAsMention();
    }

    private String typeName(String guildId, CommunityAlertType type) {
        return message(guildId, "community_event.alert_type." + type.name());
    }

    private String message(String guildId, String key, Object... args) {
        return messageService.getMessage(guildId, key, args);
    }
}
