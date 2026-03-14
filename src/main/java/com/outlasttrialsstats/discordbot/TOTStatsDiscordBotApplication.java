package com.outlasttrialsstats.discordbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TOTStatsDiscordBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TOTStatsDiscordBotApplication.class, args);
    }

}
