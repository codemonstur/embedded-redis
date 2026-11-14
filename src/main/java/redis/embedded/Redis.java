package redis.embedded;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The {@code Redis} interface.
 */
public interface Redis {
    /**
     * The {@code DEFAULT_REDIS_PORT} field.
     */
    int DEFAULT_REDIS_PORT = 6379;
    /**
     * The {@code SERVER_READY_PATTERN} field.
     */
    Pattern SERVER_READY_PATTERN = Pattern.compile(".*[Rr]eady to accept connections.*");
    /**
     * The {@code SENTINEL_READY_PATTERN} field.
     */
    Pattern SENTINEL_READY_PATTERN = Pattern.compile(".*Sentinel (runid|ID) is.*");

    /**
     * Returns whether the active condition holds.
     *
     * @return whether the condition holds
     */
    boolean isActive();

    /**
     * Performs the {@code start} operation.
     *
     * @throws IOException if the operation fails
     */
    void start() throws IOException;

    /**
     * Performs the {@code stop} operation.
     *
     * @throws IOException if the operation fails
     */
    void stop() throws IOException;

    /**
     * Returns the result of {@code ports}.
     *
     * @return the result
     */
    List<Integer> ports();
}
