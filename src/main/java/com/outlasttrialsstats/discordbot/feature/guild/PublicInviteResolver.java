package com.outlasttrialsstats.discordbot.feature.guild;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.channel.unions.DefaultGuildChannelUnion;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PublicInviteResolver {

    static final String DISCOVERABLE = "DISCOVERABLE";

    public String resolve(Guild guild, String storedUrl) {
        String vanityUrl = guild.getVanityUrl();
        if (vanityUrl != null) {
            return vanityUrl;
        }
        if (!guild.getFeatures().contains(DISCOVERABLE)) {
            return null;
        }
        if (storedUrl != null && stillValid(guild, storedUrl)) {
            return storedUrl;
        }
        return createInvite(guild);
    }

    private boolean stillValid(Guild guild, String url) {
        try {
            Invite invite = Invite.resolve(guild.getJDA(), url.substring(url.lastIndexOf('/') + 1)).complete();
            return invite.getGuild() != null && guild.getId().equals(invite.getGuild().getId());
        } catch (Exception e) {
            return false;
        }
    }

    private String createInvite(Guild guild) {
        DefaultGuildChannelUnion channel = guild.getDefaultChannel();
        if (channel == null || !guild.getSelfMember().hasPermission(channel, Permission.CREATE_INSTANT_INVITE)) {
            return null;
        }
        try {
            return channel.createInvite().setMaxAge(0).setMaxUses(0).setUnique(false).complete().getUrl();
        } catch (Exception e) {
            log.warn("Failed to create public invite for guild {}: {}", guild.getId(), e.getMessage());
            return null;
        }
    }
}
