package com.outlasttrialsstats.discordbot.config;

import io.github.kaktushose.jdac.JDACommands;
import com.outlasttrialsstats.discordbot.TOTStatsDiscordBotApplication;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JdaConfiguration {

    @Value("${discord.bot.token}")
    private String token;

    @Bean
    public JDA jda(List<ListenerAdapter> listeners) throws InterruptedException {
        return JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES)
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.watching("outlasttrialsstats.com/verify"))
                .addEventListeners(listeners.toArray())
                .build()
                .awaitReady();
    }

    @Bean
    public JDACommands jdaCommands(JDA jda, ApplicationContext applicationContext) {
        return JDACommands.builder(jda, TOTStatsDiscordBotApplication.class)
                .instanceProvider(applicationContext::getBean)
                .errorMessageFactory(new BotErrorMessageFactory())
                .start();
    }
}
