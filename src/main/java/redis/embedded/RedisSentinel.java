package redis.embedded;

import redis.embedded.core.RedisSentinelBuilder;

import java.util.List;
import java.util.function.Consumer;

public final class RedisSentinel extends RedisInstance {

    public RedisSentinel(final int port, final List<String> args, final boolean forceStop) {
        super(port, args, SENTINEL_READY_PATTERN, forceStop, null, null);
    }

    public RedisSentinel(final int port, final List<String> args, final boolean forceStop,
                         final Consumer<String> soutListener, final Consumer<String> serrListener) {
        super(port, args, SERVER_READY_PATTERN, forceStop, soutListener, serrListener);
    }

    public static RedisSentinelBuilder newRedisSentinel() { return new RedisSentinelBuilder(); }

}
