package redis.embedded;

import java.util.List;
import java.util.regex.Pattern;

public class RedisSentinel extends RedisInstance {
    public static final Pattern SENTINEL_READY_PATTERN = Pattern.compile(".*Sentinel (runid|ID) is.*");

    public RedisSentinel(final int port, final List<String> args) {
        super(port, args, SENTINEL_READY_PATTERN);
    }

    public static RedisSentinelBuilder newRedisSentinel() { return new RedisSentinelBuilder(); }

}
