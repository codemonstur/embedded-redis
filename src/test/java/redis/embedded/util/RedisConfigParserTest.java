package redis.embedded.util;

import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.platform.commons.JUnitException;
import redis.embedded.model.RedisConfig;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@DisplayName("Tests for RedisConfigParser")
class RedisConfigParserTest {

    private final RedisConfigParser parser = new RedisConfigParser();

    private Path givenFile;
    private ThrowingCallable parse;
    private RedisConfig result;

    @DisplayName("Official self documented examples for Redis should be parsed without error")
    @ParameterizedTest(name = "{0} should be parsed without error")
    @ArgumentsSource(OfficialExamples.class)
    void officialSelfDocumentedExample_whenParsing_shouldSucceed(Path file) {
        givenFile(file);
        whenParsing();
        thenValidRedisConfShouldBeReturned();
    }

    @DisplayName("Invalid redis.conf should yield error when being parsed")
    @ParameterizedTest(name = "{0} should yield error message -> {1}")
    @ArgumentsSource(InvalidExamples.class)
    void invalidExample_whenParsing_shouldYieldError(Path file, String expectedMsg) {
        givenFile(file);
        whenParsing();
        thenErrorShouldBeThrown().message().contains(expectedMsg);
    }

    @DisplayName("Valid redis.conf should be parsed correctly with expected directives")
    @ParameterizedTest(name = "{0} should be parsed without error and yield expected RedisConf")
    @ArgumentsSource(ValidExamples.class)
    void validExample_whenParsing_shouldYieldExpectedConf(Path file, RedisConfig expected) {
        givenFile(file);
        whenParsing();
        thenParsedConfShouldBeAs(expected);
    }

    private void givenFile(Path file) {
        givenFile = file;
    }

    private void whenParsing() {
        parse = () -> { result = parser.parse(givenFile); };
    }

    private void thenValidRedisConfShouldBeReturned() {
        Assertions.assertThatCode(parse).doesNotThrowAnyException();
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.directives()).isNotEmpty();
    }

    private void thenParsedConfShouldBeAs(final RedisConfig expected) {
        Assertions.assertThatCode(parse).doesNotThrowAnyException();
        Assertions.assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    private AbstractThrowableAssert<?, ?> thenErrorShouldBeThrown() {
        return Assertions.assertThatThrownBy(parse).isExactlyInstanceOf(IllegalArgumentException.class);
    }

    public static class OfficialExamples implements ArgumentsProvider {

        private final Path dir = Paths.get("src/test/resources/redis-conf/official-examples");

        @Override
        public Stream<Arguments> provideArguments(ExtensionContext context) {

            try (DirectoryStream<Path> versions = Files.newDirectoryStream(dir)) {
                return StreamSupport.stream(versions.spliterator(), false)
                        .map(confFile -> Arguments.of(Named.of("Example for version " + confFile.getFileName(), confFile.resolve("redis.conf"))))
                        .collect(Collectors.toList())
                        .stream();
            } catch (IOException e) {
                throw new JUnitException("Error while reading redis.conf examples.", e);
            }
        }
    }

    public static class InvalidExamples implements ArgumentsProvider {

        private final Path dir = Paths.get("src/test/resources/redis-conf/invalid-examples");

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(Named.of("Conf with argument has unbalanced double quote", dir.resolve("argument-unbalanced-double-quote.conf")), "Unbalanced quotes in arguments: '6379\"'"),
                    Arguments.of(Named.of("Conf with argument has unbalanced single quote", dir.resolve("argument-unbalanced-single-quote.conf")), "Unbalanced quotes in arguments: ''6379'"),
                    Arguments.of(Named.of("Conf with keyword has double quotes", dir.resolve("double-quoted-keyword.conf")), "Keyword '\"port\"' contains illegal characters. Only alphanumeric characters, hyphens and underscores are allowed"),
                    Arguments.of(Named.of("Conf with keyword has single quotes", dir.resolve("single-quoted-keyword.conf")), "Keyword ''port'' contains illegal characters. Only alphanumeric characters, hyphens and underscores are allowed"),
                    Arguments.of(Named.of("Conf with keyword that contains hash", dir.resolve("keyword-contains-hash.conf")), "Keyword 'po#rt' contains illegal characters. Only alphanumeric characters, hyphens and underscores are allowed"),
                    Arguments.of(Named.of("Conf with missing arguments", dir.resolve("missing-arguments.conf")), "Illegal line in redis.conf: 'port'")
            );
        }
    }

    public static class ValidExamples implements ArgumentsProvider {

        private final Path dir = Paths.get("src/test/resources/redis-conf/valid-examples");

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(Named.of("Empty config file", dir.resolve("empty.conf")), new RedisConfig(Collections.emptyList())),
                    Arguments.of(Named.of("Conf with duplicate keywords", dir.resolve("with-duplicate-keywords.conf")), new RedisConfig(Arrays.asList(
                            new RedisConfig.Directive("bind", "localhost"),
                            new RedisConfig.Directive("bind", "127.0.0.1", "::1"),
                            new RedisConfig.Directive("port", "6379")
                    ))),
                    Arguments.of(Named.of("Conf with escaped arguments", dir.resolve("with-escaped-arguments.conf")), new RedisConfig(Arrays.asList(
                            new RedisConfig.Directive("tls-protocols", "TLSv1.2 TLSv1.3"),
                            new RedisConfig.Directive("logfile", ""),
                            new RedisConfig.Directive("proc-title-template", "{title} {listen-addr} {server-mode}"),
                            new RedisConfig.Directive("sentinel", "monitor", "Blue-lored Antbird", "::1", "6379", "1"),
                            new RedisConfig.Directive("double-quote", "\""),
                            new RedisConfig.Directive("single-quote", "'")
                    ))),
                    Arguments.of(Named.of("Conf without duplicate keywords", dir.resolve("without-duplicate-keywords.conf")), new RedisConfig(Arrays.asList(
                            new RedisConfig.Directive("bind", "i.like.trains.org"),
                            new RedisConfig.Directive("port", "6379")
                    )))
            );
        }
    }
}
