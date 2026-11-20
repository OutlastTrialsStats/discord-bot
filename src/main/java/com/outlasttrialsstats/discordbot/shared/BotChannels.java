package com.outlasttrialsstats.discordbot.shared;

import net.dv8tion.jda.api.Permission;

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
}
