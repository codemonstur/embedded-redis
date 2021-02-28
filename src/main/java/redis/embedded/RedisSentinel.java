package redis.embedded;

import redis.embedded.core.RedisSentinelBuilder;

import java.util.List;

public final class RedisSentinel extends RedisInstance {

    public RedisSentinel(final int port, final List<String> args) {
        super(port, args, SENTINEL_READY_PATTERN);
    }

    public static RedisSentinelBuilder newRedisSentinel() { return new RedisSentinelBuilder(); }

}
