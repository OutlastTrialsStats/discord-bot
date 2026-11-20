package com.outlasttrialsstats.discordbot.feature.communityevent;

import com.outlasttrialsstats.backend.api.model.DiscordCommunityEvent;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventBinding;
import com.outlasttrialsstats.discordbot.feature.communityevent.service.CommunityEventService;
import com.outlasttrialsstats.discordbot.shared.MessageCleanup;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommunityEventMessagePublisher {

    private final CommunityEventService service;
    private final CommunityEventEmbedFactory embedFactory;
    private final CommunityEventScheduler scheduler;

    public sealed interface Outcome {
        record Posted(String eventName, boolean followsCurrent) implements Outcome {}
        record NoCurrentEvent() implements Outcome {}
        record UnknownEvent(String eventId) implements Outcome {}
        record FetchFailed() implements Outcome {}
        record PostFailed() implements Outcome {}
    }

    public Outcome publish(Guild guild, StandardGuildMessageChannel channel, String eventId) {
        String guildId = guild.getId();
        Optional<List<DiscordCommunityEvent>> events = service.getEvents();
        if (events.isEmpty()) {
            return new Outcome.FetchFailed();
        }

        Instant now = Instant.now();
        Optional<DiscordCommunityEvent> shown = eventId == null
                ? CommunityEvents.selectCurrent(events.get(), now)
                : CommunityEvents.find(events.get(), eventId);
        if (shown.isEmpty()) {
            return eventId == null ? new Outcome.NoCurrentEvent() : new Outcome.UnknownEvent(eventId);
        }

        Message posted = null;
        Optional<CommunityEventBinding> previous;
        try {
            posted = channel.sendMessageEmbeds(embedFactory.buildProgressEmbed(guildId, shown.get(), now))
                    .complete();
            previous = service.saveBinding(guildId, eventId, shown.get().getId(), channel.getId(), posted.getId());
        } catch (Exception e) {
            log.warn("Failed to set up community event message in guild {}: {}", guildId, e.getMessage());
            if (posted != null) {
                posted.delete().queue(_ -> {}, _ -> {});
            }
            return new Outcome.PostFailed();
        }
        scheduler.forget(guildId);
        previous.ifPresent(binding -> deleteMessage(guild, binding));
        return new Outcome.Posted(shown.get().getName(), eventId == null);
    }

    public static void deleteMessage(Guild guild, CommunityEventBinding binding) {
        StandardGuildMessageChannel channel = guild.getChannelById(StandardGuildMessageChannel.class,
                binding.channelId());
        if (channel != null) {
            MessageCleanup.deleteMessages(channel, List.of(binding.messageId()), Set.of());
        }
    }
}
