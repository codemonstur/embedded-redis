package redis.embedded;

import redis.embedded.core.RedisSentinelBuilder;

import java.util.List;
import java.util.function.Consumer;

/**
 * The {@code RedisSentinel} class.
 */
public final class RedisSentinel extends RedisInstance {

    /**
     * Creates a new instance.
     *
     * @param port the port
     * @param args the command-line arguments
     * @param forceStop the force stop
     */
    public RedisSentinel(final int port, final List<String> args, final boolean forceStop) {
        super(port, args, SENTINEL_READY_PATTERN, forceStop, null, null);
    }

    /**
     * Creates a new instance.
     *
     * @param port the port
     * @param args the command-line arguments
     * @param forceStop the force stop
     * @param soutListener the sout listener
     * @param serrListener the serr listener
     */
    public RedisSentinel(final int port, final List<String> args, final boolean forceStop,
                         final Consumer<String> soutListener, final Consumer<String> serrListener) {
        super(port, args, SENTINEL_READY_PATTERN, forceStop, soutListener, serrListener);
    }

    /**
     * Returns the result of {@code newRedisSentinel}.
     *
     * @return the result
     */
    public static RedisSentinelBuilder newRedisSentinel() { return new RedisSentinelBuilder(); }

}
