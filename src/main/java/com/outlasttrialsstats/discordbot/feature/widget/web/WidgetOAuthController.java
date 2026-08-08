package com.outlasttrialsstats.discordbot.feature.widget.web;

import com.outlasttrialsstats.discordbot.feature.widget.config.WidgetProperties;
import com.outlasttrialsstats.discordbot.feature.widget.service.WidgetEnrollmentService;
import com.outlasttrialsstats.discordbot.feature.widget.service.WidgetEnrollmentService.CallbackResult;
import com.outlasttrialsstats.discordbot.feature.widget.service.WidgetPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequiredArgsConstructor
@Slf4j
public class WidgetOAuthController {

    private final WidgetEnrollmentService enrollmentService;
    private final WidgetPushService pushService;
    private final WidgetProperties widgetProperties;

    @GetMapping(value = "/oauth/widget/callback", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<ResponseEntity<String>> callback(@RequestParam(required = false) String code,
                                                 @RequestParam(required = false) String state,
                                                 @RequestParam(required = false) String error) {
        // Enrollment completion is blocking (JPA + blocking HTTP calls), keep it off the event loop.
        return Mono.fromCallable(() -> handleCallback(code, state, error))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ResponseEntity<String> handleCallback(String code, String state, String error) {
        if (!widgetProperties.enabled()) {
            return page(HttpStatus.SERVICE_UNAVAILABLE, "Widget unavailable",
                    "The profile widget feature is currently disabled. Please try again later.");
        }

        if (error != null) {
            return page(HttpStatus.OK, "Authorization declined",
                    "You declined the authorization. No widget was added to your profile. "
                            + "You can run <code>/widget enable</code> again at any time.");
        }

        if (code == null || state == null) {
            return page(HttpStatus.BAD_REQUEST, "Invalid request",
                    "This link is invalid. Please run <code>/widget enable</code> in Discord to get a new link.");
        }

        return switch (enrollmentService.completeEnrollment(code, state)) {
            case CallbackResult.Success(String discordUserId) -> {
                // The push is blocking I/O — keep it off the common pool and off the event loop.
                Schedulers.boundedElastic().schedule(() -> pushService.pushOne(discordUserId));
                yield page(HttpStatus.OK, "Widget enabled!",
                        "Your Outlast Trials stats widget has been enabled. "
                                + "Your stats will appear on your Discord profile shortly. "
                                + "You can close this page.");
            }
            case CallbackResult.InvalidState _ -> page(HttpStatus.BAD_REQUEST, "Invalid link",
                    "This authorization link is not valid. "
                            + "Please run <code>/widget enable</code> in Discord to get a new link.");
            case CallbackResult.Expired _ -> page(HttpStatus.BAD_REQUEST, "Link expired",
                    "This authorization link has expired. "
                            + "Please run <code>/widget enable</code> in Discord to get a new link.");
            case CallbackResult.ExchangeFailed _ -> page(HttpStatus.BAD_GATEWAY, "Something went wrong",
                    "We could not complete the authorization with Discord. Please try again later.");
            case CallbackResult.UserMismatch _ -> page(HttpStatus.FORBIDDEN, "Account mismatch",
                    "The Discord account that authorized does not match the account that requested the widget. "
                            + "Please run <code>/widget enable</code> with the correct account.");
        };
    }

    private static ResponseEntity<String> page(HttpStatus status, String title, String message) {
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>%1$s — Outlast Trials Stats</title>
                    <style>
                        body { margin: 0; font-family: system-ui, sans-serif; background: #1e1f22; color: #dbdee1;
                               display: flex; align-items: center; justify-content: center; min-height: 100vh; }
                        main { max-width: 28rem; padding: 2rem; text-align: center; }
                        h1 { color: #f2f3f5; font-size: 1.4rem; }
                        code { background: #2b2d31; padding: 0.15em 0.4em; border-radius: 4px; }
                    </style>
                </head>
                <body>
                    <main>
                        <h1>%1$s</h1>
                        <p>%2$s</p>
                    </main>
                </body>
                </html>
                """.formatted(title, message);
        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(html);
    }
}
