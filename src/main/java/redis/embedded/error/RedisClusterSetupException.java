package redis.embedded.error;

/**
 * The {@code RedisClusterSetupException} class.
 */
public class RedisClusterSetupException extends Exception {

    /**
     * Creates a new instance.
     *
     * @param message the message
     */
    public RedisClusterSetupException(final String message) { super(message); }

    /**
     * Creates a new instance.
     *
     * @param message the message
     * @param cause the cause
     */
    public RedisClusterSetupException(final String message, final Throwable cause) { super(message, cause); }

}
