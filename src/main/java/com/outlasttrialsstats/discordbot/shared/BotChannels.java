package com.outlasttrialsstats.discordbot.shared;

import java.util.Optional;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;

public final class BotChannels {

    public static final Permission[] SEND_EMBEDS = {
            Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS
    };

    public static final Permission[] SEND_AND_EDIT_EMBEDS = {
            Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS,
            Permission.MESSAGE_HISTORY
    };

    private BotChannels() {
    }

    public static Optional<StandardGuildMessageChannel> asTextOrNews(GuildChannelUnion channel) {
        if (channel.getType() != ChannelType.TEXT && channel.getType() != ChannelType.NEWS) {
            return Optional.empty();
        }
        return Optional.of(channel.asStandardGuildMessageChannel());
    }
}
