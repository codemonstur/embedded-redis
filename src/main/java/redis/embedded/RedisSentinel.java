package redis.embedded;

import redis.embedded.core.RedisSentinelBuilder;

import java.util.List;

public final class RedisSentinel extends RedisInstance {

    public RedisSentinel(final int port, final List<String> args, final boolean forceStop) {
        super(port, args, SENTINEL_READY_PATTERN, forceStop);
    }

    public static RedisSentinelBuilder newRedisSentinel() { return new RedisSentinelBuilder(); }

}
