package com.outlasttrialsstats.discordbot.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MessageBundleConsistencyTest {

    private static final String BASE = "messages/messages.properties";
    private static final String GERMAN = "messages/messages_de.properties";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)[,}]");

    static Stream<String> bundles() {
        return Stream.of(BASE, GERMAN);
    }

    @Test
    void bundles_haveSameKeys() {
        Properties base = load(BASE);
        Properties german = load(GERMAN);

        assertThat(german.stringPropertyNames()).containsExactlyInAnyOrderElementsOf(base.stringPropertyNames());
    }

    @Test
    void bundles_haveSamePlaceholdersPerKey() {
        Properties base = load(BASE);
        Properties german = load(GERMAN);

        for (String key : base.stringPropertyNames()) {
            assertThat(placeholders(german.getProperty(key)))
                    .as("placeholders of %s", key)
                    .isEqualTo(placeholders(base.getProperty(key)));
        }
    }

    @ParameterizedTest
    @MethodSource("bundles")
    void allValues_parseWithMessageFormat(String bundle) {
        Properties properties = load(bundle);

        for (String key : properties.stringPropertyNames()) {
            assertThatCode(() -> new MessageFormat(properties.getProperty(key)))
                    .as("%s in %s", key, bundle)
                    .doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @MethodSource("bundles")
    void apostrophes_areEscaped(String bundle) {
        Properties properties = load(bundle);

        for (String key : properties.stringPropertyNames()) {
            String withoutEscapedQuotes = properties.getProperty(key).replace("''", "");
            assertThat(withoutEscapedQuotes)
                    .as("%s in %s must escape apostrophes as ''", key, bundle)
                    .doesNotContain("'");
        }
    }

    private static Set<String> placeholders(String value) {
        Set<String> result = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static Properties load(String path) {
        Properties properties = new Properties();
        try (InputStream in = MessageBundleConsistencyTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return properties;
    }
}
