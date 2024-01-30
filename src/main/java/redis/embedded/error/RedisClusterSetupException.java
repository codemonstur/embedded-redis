package redis.embedded.error;

public class RedisClusterSetupException extends Exception {

    public RedisClusterSetupException(final String message) { super(message); }

    public RedisClusterSetupException(final String message, final Throwable cause) { super(message, cause); }

}
