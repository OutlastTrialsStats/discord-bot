package com.outlasttrialsstats.discordbot.feature.communityevent.service;

import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import com.outlasttrialsstats.discordbot.entity.CommunityAlertType;
import com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEventEmbedFactory;
import com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEventMessagePublisher;
import com.outlasttrialsstats.discordbot.feature.communityevent.CommunityEvents;
import com.outlasttrialsstats.discordbot.feature.communityevent.alert.AlertRule;
import com.outlasttrialsstats.discordbot.feature.setup.RoleSafety;
import com.outlasttrialsstats.discordbot.shared.BotChannels;
import com.outlasttrialsstats.discordbot.shared.MessageService;
import com.outlasttrialsstats.discordbot.shared.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommunityEventSettings {

    public static final int[] LEAD_TIMES = {15, 60, 180};

    private final CommunityEventService service;
    private final CommunityEventMessagePublisher publisher;
    private final MessageService messageService;

    public String postMessage(Guild guild, StandardGuildMessageChannel channel, String eventId) {
        String guildId = guild.getId();
        if (!guild.getSelfMember().hasPermission(channel, BotChannels.SEND_AND_EDIT_EMBEDS)) {
            return message(guildId, "error.missing_permission.channel_write", channel.getAsMention());
        }
        return describe(guildId, publisher.publish(guild, channel, eventId), channel);
    }

    public String describe(String guildId, CommunityEventMessagePublisher.Outcome outcome,
                           StandardGuildMessageChannel channel) {
        return switch (outcome) {
            case CommunityEventMessagePublisher.Outcome.Posted posted -> message(guildId, posted.followsCurrent()
                            ? "setup.community_event.message.success_auto"
                            : "setup.community_event.message.success",
                    posted.eventName(), channel.getAsMention());
            case CommunityEventMessagePublisher.Outcome.NoCurrentEvent _ ->
                    message(guildId, "setup.community_event.no_event");
            case CommunityEventMessagePublisher.Outcome.UnknownEvent unknown ->
                    message(guildId, "setup.community_event.unknown_event", unknown.eventId());
            case CommunityEventMessagePublisher.Outcome.FetchFailed _ -> message(guildId, "community_event.error");
            case CommunityEventMessagePublisher.Outcome.PostFailed _ ->
                    message(guildId, "setup.community_event.message.post_failed", channel.getAsMention());
            case CommunityEventMessagePublisher.Outcome.LimitReached _ -> message(guildId,
                    "setup.community_event.message.limit", String.valueOf(CommunityEventService.MAX_MESSAGES_PER_GUILD));
        };
    }

    public List<Choice> eventChoices(String typed) {
        String needle = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        return service.getCachedEvents().stream()
                .filter(e -> e.getId().toLowerCase(Locale.ROOT).contains(needle)
                        || e.getName().toLowerCase(Locale.ROOT).contains(needle))
                .limit(OptionData.MAX_CHOICES)
                .map(e -> new Choice(Texts.truncate(e.getName() + " (" + e.getId() + ")", OptionData.MAX_CHOICE_NAME_LENGTH), e.getId()))
                .toList();
    }

    public List<DiscordCommunityEvent> knownEvents() {
        return service.getCachedEvents();
    }

    public List<CommunityEventBinding> bindings(String guildId) {
        return service.getBindings(guildId);
    }

    public Optional<String> alertChannelId(String guildId) {
        return service.getAlertChannelId(guildId);
    }

    public Map<CommunityAlertType, AlertRule> rules(String guildId) {
        return service.getRules(guildId);
    }

    public String bindingLabel(Guild guild, CommunityEventBinding binding) {
        StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class,
                binding.channelId());
        String target = targetLabel(guild.getId(), binding);
        return channel == null ? target : target + " · #" + channel.getName();
    }

    private String targetLabel(String guildId, CommunityEventBinding binding) {
        String eventName = CommunityEvents.find(service.getCachedEvents(), binding.shownEventId())
                .map(DiscordCommunityEvent::getName)
                .orElse(binding.shownEventId());
        return binding.followsCurrentEvent()
                ? message(guildId, "setup.community_event.status.message_auto") + " · " + eventName
                : eventName;
    }

    public String setAlertChannel(Guild guild, StandardGuildMessageChannel channel) {
        String guildId = guild.getId();
        if (!guild.getSelfMember().hasPermission(channel, BotChannels.SEND_EMBEDS)) {
            return message(guildId, "error.missing_permission.alert_channel", channel.getAsMention());
        }
        service.setAlertChannel(guildId, channel.getId());
        return message(guildId, "setup.community_event.alerts.success", channel.getAsMention());
    }

    public String setEnabled(Guild guild, CommunityAlertType type, boolean enabled) {
        service.updateRule(guild.getId(), type, enabled, null, false, null);
        return updatedNotes(guild, type, null);
    }

    public String setRole(Guild guild, CommunityAlertType type, Role role) {
        String guildId = guild.getId();
        if (role != null && role.isManaged()) {
            return message(guildId, RoleSafety.Violation.MANAGED_ROLE.getMessageKey(), role.getName());
        }
        service.updateRule(guildId, type, null, role == null ? null : role.getId(), role == null, null);
        return updatedNotes(guild, type, role);
    }

    public String setLeadTime(Guild guild, CommunityAlertType type, int leadTimeMinutes) {
        service.updateRule(guild.getId(), type, null, null, false, leadTimeMinutes);
        return updatedNotes(guild, type, null);
    }

    private String updatedNotes(Guild guild, CommunityAlertType type, Role role) {
        String guildId = guild.getId();
        List<String> notes = new ArrayList<>();
        notes.add(message(guildId, "setup.community_event.alert.updated", typeName(guildId, type)));
        Optional<String> alertChannelId = service.getAlertChannelId(guildId);
        if (alertChannelId.isEmpty()) {
            notes.add(message(guildId, "setup.community_event.alert.no_channel"));
        } else if (role != null) {
            StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class,
                    alertChannelId.get());
            if (channel != null && !canPing(guild, channel, role)) {
                notes.add(message(guildId, "setup.community_event.alert.role_not_pingable",
                        roleMention(role), channel.getAsMention()));
            }
        }
        return String.join("\n", notes);
    }

    static boolean canPing(Guild guild, StandardGuildMessageChannel channel, Role role) {
        if (!role.isPublicRole() && role.isMentionable()) {
            return true;
        }
        return guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_MENTION_EVERYONE);
    }

    public MessageEmbed statusEmbed(Guild guild) {
        String guildId = guild.getId();
        List<String> lines = new ArrayList<>();

        List<CommunityEventBinding> bindings = service.getBindings(guildId);
        if (bindings.isEmpty()) {
            lines.add(message(guildId, "setup.community_event.status.message_none"));
        }
        for (CommunityEventBinding binding : bindings) {
            lines.add(message(guildId, "setup.community_event.status.message", "<#" + binding.channelId() + ">",
                    targetLabel(guildId, binding)));
        }
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

    public static String formatLeadTime(int minutes) {
        return minutes % 60 == 0 ? (minutes / 60) + " h" : minutes + " min";
    }

    public String removeMessage(Guild guild, long bindingId) {
        String guildId = guild.getId();
        Optional<CommunityEventBinding> removed = service.removeBinding(guildId, bindingId);
        removed.ifPresent(binding -> CommunityEventMessagePublisher.deleteMessage(guild, binding));
        return message(guildId, removed.isPresent()
                ? "setup.community_event.remove.message"
                : "setup.community_event.remove.nothing");
    }

    public String disableAlerts(Guild guild) {
        String guildId = guild.getId();
        return message(guildId, service.removeAlertChannel(guildId)
                ? "setup.community_event.remove.alerts"
                : "setup.community_event.remove.nothing");
    }

    private static String roleMention(Role role) {
        return role.isPublicRole() ? "@everyone" : role.getAsMention();
    }

    public String typeName(String guildId, CommunityAlertType type) {
        return message(guildId, "community_event.alert_type." + type.name());
    }

    private String message(String guildId, String key, Object... args) {
        return messageService.getMessage(guildId, key, args);
    }
}
