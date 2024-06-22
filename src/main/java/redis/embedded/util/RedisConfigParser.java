package redis.embedded.util;

import static redis.embedded.util.RedisConfigParser.ArgsParseState.*;
import redis.embedded.model.RedisConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Parses a {@code redis.conf} file.
 */
public class RedisConfigParser {

    /**
     * Parses the given {@code redis.conf} file.
     * @param file the {@code redis.conf} file to parse
     * @return the parsed {@link RedisConfig} file
     * @throws IllegalArgumentException if the file is not a valid {@code redis.conf} file
     */
    public RedisConfig parse(final Path file) throws IOException {
        final List<RedisConfig.Directive> directives = Files.readAllLines(file)
            .stream()
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .filter(line -> !isComment(line))
            .map(this::parseDirective)
            .collect(Collectors.toList());
        return new RedisConfig(directives);
    }

    private boolean isComment(final String line) {
        return line.startsWith("#");
    }

    private RedisConfig.Directive parseDirective(final String line) {
        final String[] keywordAndArgs = line.split("\\s+", 2);
        if (keywordAndArgs.length <= 1) {
            throw new IllegalArgumentException("Illegal line in redis.conf: '" + line + "'");
        }
        final String keyword = keywordAndArgs[0];
        final String arguments = keywordAndArgs[1];
        return new RedisConfig.Directive(keyword, parseArguments(arguments));
    }

    private List<String> parseArguments(final String rawArguments) {
        final List<String> arguments = new ArrayList<>();
        ArgsParseState state = UNESCAPED;
        StringBuilder currentArg = new StringBuilder();
        for (char c : rawArguments.toCharArray()) {
            if (c == '"' && state == UNESCAPED) {
                state = ESCAPED_DOUBLE;
            } else if (c == '\'' && state == UNESCAPED) {
                state = ESCAPED_SINGLE;
            } else if (c == '"' && state == ESCAPED_DOUBLE) {
                state = UNESCAPED;
            } else if (c == '\'' && state == ESCAPED_SINGLE) {
                state = UNESCAPED;
            } else if (c == ' ' && state == UNESCAPED) {
                arguments.add(currentArg.toString());
                currentArg = new StringBuilder();
            } else {
                currentArg.append(c);
            }
        }

        if (state != UNESCAPED) {
            throw new IllegalArgumentException("Unbalanced quotes in arguments: '" + rawArguments + "'");
        } else {
            arguments.add(currentArg.toString());
            return arguments;
        }
    }

    enum ArgsParseState {
        UNESCAPED, ESCAPED_SINGLE, ESCAPED_DOUBLE
    }
}