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
        final List<Redis> started = new ArrayList<>();
        try {
            for (final Redis redis : sentinels) {
                redis.start();
                started.add(redis);
            }
            for (final Redis redis : servers) {
                redis.start();
                started.add(redis);
            }
        } catch (final IOException | RuntimeException e) {
            // Don't leave half a cluster running: the started instances would keep their ports bound
            try {
                stopAll(started);
            } catch (final IOException stopFailure) {
                e.addSuppressed(stopFailure);
            }
            throw e;
        }
    }

    @Override
    public void stop() throws IOException {
        final List<Redis> all = new ArrayList<>(sentinels);
        all.addAll(servers);
        stopAll(all);
    }

    // Stops every instance even if one of them fails, so a single wedged process doesn't leak the others
    private static void stopAll(final List<Redis> instances) throws IOException {
        IOException failure = null;
        for (final Redis redis : instances) {
            try {
                redis.stop();
            } catch (final IOException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        if (failure != null) throw failure;
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
