package com.outlasttrialsstats.discordbot.config;

import io.github.kaktushose.jdac.embeds.error.ErrorMessageFactory;
import io.github.kaktushose.proteus.conversion.ConversionResult;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jspecify.annotations.NonNull;

import java.awt.*;

@Slf4j
public class BotErrorMessageFactory implements ErrorMessageFactory {

    @Override
    public @NonNull MessageCreateData getCommandExecutionFailedMessage(@NonNull ErrorContext context, @NonNull Throwable throwable) {
        log.error("Command execution failed for interaction: {}", context, throwable);

        return new MessageCreateBuilder()
                .setEmbeds(new EmbedBuilder()
                        .setTitle("Something went wrong")
                        .setDescription("An error occurred while executing this command. Please try again later.")
                        .setColor(Color.RED)
                        .build())
                .build();
    }

    @Override
    public @NonNull MessageCreateData getInsufficientPermissionsMessage(@NonNull ErrorContext context) {
        return new MessageCreateBuilder()
                .setEmbeds(new EmbedBuilder()
                        .setTitle("Insufficient Permissions")
                        .setDescription("You don't have the required permissions to use this command.")
                        .setColor(Color.ORANGE)
                        .build())
                .build();
    }

    @Override
    public @NonNull MessageCreateData getConstraintFailedMessage(@NonNull ErrorContext context, @NonNull String message) {
        return new MessageCreateBuilder()
                .setEmbeds(new EmbedBuilder()
                        .setTitle("Invalid Input")
                        .setDescription(message)
                        .setColor(Color.ORANGE)
                        .build())
                .build();
    }

    @Override
    public MessageCreateData getTypeAdaptingFailedMessage(@NonNull ErrorContext context, ConversionResult.@NonNull Failure<?> failure) {
        return new MessageCreateBuilder()
                .setEmbeds(new EmbedBuilder()
                        .setTitle("Invalid Argument")
                        .setDescription("One or more arguments could not be processed. Please check your input.")
                        .setColor(Color.ORANGE)
                        .build())
                .build();
    }

    @Override
    public @NonNull MessageCreateData getTimedOutComponentMessage(@NonNull GenericInteractionCreateEvent event) {
        return new MessageCreateBuilder()
                .setEmbeds(new EmbedBuilder()
                        .setTitle("Timed Out")
                        .setDescription("This interaction has expired. Please run the command again.")
                        .setColor(Color.GRAY)
                        .build())
                .build();
    }
}
