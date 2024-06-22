package redis.embedded.model;

import redis.embedded.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Representation of a {@code redis.conf} file, i.e. the configuration of a Redis server.
 */
public class RedisConfig {

    private final List<Directive> directives;

    public RedisConfig(final List<Directive> directives) {
        Objects.requireNonNull(directives);
        this.directives = Collections.unmodifiableList(directives);
    }

    public List<Directive> directives() {
        return directives;
    }

    public List<Directive> directives(final String keyword) {
        return directives.stream().filter(dir -> dir.keyword().equals(keyword)).collect(Collectors.toList());
    }

    public static class Directive {

        private final String keyword;

        private final List<String> arguments;

        public Directive(final String keyword, final String... arguments) {
            this(keyword, Arrays.asList(arguments));
        }

        public Directive(final String keyword, final List<String> arguments) {
            require(StringUtils::isNotBlank, keyword, "Keyword must not be blank");
            require(str -> str.matches("[a-zA-Z0-9_-]+"), keyword, "Keyword '" + keyword + "' contains illegal characters. Only alphanumeric characters, hyphens and underscores are allowed");
            require(list -> list != null && !list.isEmpty(), arguments, "At least one argument is required");

            this.keyword = Objects.requireNonNull(keyword);
            this.arguments = Collections.unmodifiableList(arguments);
        }

        public String keyword() {
            return keyword;
        }

        public List<String> arguments() {
            return arguments;
        }

        private <T> void require(final Predicate<T> predicate, final T value, final String message) {
            if (!predicate.test(value)) {
                throw new IllegalArgumentException(message);
            }
        }
    }
}
