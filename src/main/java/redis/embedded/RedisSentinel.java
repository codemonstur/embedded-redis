package redis.embedded;

import java.util.ArrayList;
import java.util.List;

public class RedisSentinel extends AbstractRedisInstance {
    public RedisSentinel(final List<String> args, final int port) {
        super(port, ".*Sentinel (runid|ID) is.*");
        this.args = new ArrayList<>(args);
    }

    public static RedisSentinelBuilder builder() { return new RedisSentinelBuilder(); }

}
