package redis.embedded;

import redis.embedded.core.RedisClusterBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public final class RedisCluster implements Redis {

    private final List<Redis> sentinels = new LinkedList<>();
    private final List<Redis> servers = new LinkedList<>();

    public RedisCluster(final List<Redis> sentinels, final List<Redis> servers) {
        this.servers.addAll(servers);
        this.sentinels.addAll(sentinels);
    }

    @Override
    public boolean isActive() {
        for (final Redis redis : sentinels) {
            if (!redis.isActive()) {
                return false;
            }
        }
        for (final Redis redis : servers) {
            if (!redis.isActive()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void start() throws IOException {
        for (final Redis redis : sentinels) {
            redis.start();
        }
        for (final Redis redis : servers) {
            redis.start();
        }
    }

    @Override
    public void stop() throws IOException {
        for (final Redis redis : sentinels) {
            redis.stop();
        }
        for (final Redis redis : servers) {
            redis.stop();
        }
    }

    @Override
    public List<Integer> ports() {
        final List<Integer> ports = new ArrayList<>();
        ports.addAll(sentinelPorts());
        ports.addAll(serverPorts());
        return ports;
    }

    public List<Redis> sentinels() {
        return new LinkedList<>(sentinels);
    }

    public List<Integer> sentinelPorts() {
        final List<Integer> ports = new ArrayList<>();
        for (final Redis redis : sentinels) {
            ports.addAll(redis.ports());
        }
        return ports;
    }

    public List<Redis> servers() {
        return new LinkedList<>(servers);
    }

    public List<Integer> serverPorts() {
        final List<Integer> ports = new ArrayList<>();
        for (final Redis redis : servers) {
            ports.addAll(redis.ports());
        }
        return ports;
    }

    public static RedisClusterBuilder newRedisCluster() {
        return new RedisClusterBuilder();
    }
}
