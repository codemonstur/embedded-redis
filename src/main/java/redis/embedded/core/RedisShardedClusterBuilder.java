package redis.embedded.core;

import redis.embedded.Redis;
import redis.embedded.RedisServer;
import redis.embedded.RedisShardedCluster;
import redis.embedded.model.Shard;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

import static redis.embedded.core.PortProvider.*;

public final class RedisShardedClusterBuilder {
    private static final Duration DEFAULT_INITIALIZATION_TIMEOUT = Duration.ofSeconds(20);

    private RedisServerBuilder serverBuilder = new RedisServerBuilder();
    private PortProvider shardPortProvider = newSequencePortProvider(6379);
    private Duration initializationTimeout = DEFAULT_INITIALIZATION_TIMEOUT;
    private final List<Shard> shards = new LinkedList<>();
    private final Map<Integer, Set<Integer>> replicasPortsByMainNodePort = new LinkedHashMap<>();

    public RedisShardedClusterBuilder withServerBuilder(final RedisServerBuilder serverBuilder) {
        this.serverBuilder = serverBuilder;
        return this;
    }

    public RedisShardedClusterBuilder serverPorts(final Collection<Integer> ports) {
        this.shardPortProvider = newPredefinedPortProvider(ports);
        return this;
    }

    public RedisShardedClusterBuilder initializationTimeout(final Duration initializationTimeout) {
        this.initializationTimeout = initializationTimeout;
        return this;
    }

    public RedisShardedClusterBuilder ephemeralServers() {
        this.shardPortProvider = newEphemeralPortProviderInRedisClusterRange();
        return this;
    }

    public RedisShardedClusterBuilder ephemeral() {
        ephemeralServers();
        return this;
    }
    public RedisShardedClusterBuilder shard(final String name, final int replicaCount) {
        this.shards.add(new Shard(name, replicaCount, this.shardPortProvider));
        return this;
    }

    public RedisShardedCluster build() throws IOException {
        final List<Redis> servers = buildServers();
        return new RedisShardedCluster(servers, replicasPortsByMainNodePort, initializationTimeout);
    }

    private List<Redis> buildServers() throws IOException {
        final List<Redis> servers = new ArrayList<>();
        for (final Shard shard : shards) {
            servers.add(buildMainNode(shard));
            servers.addAll(buildReplicas(shard));
        }
        return servers;
    }

    private List<RedisServer> buildReplicas(final Shard shard) throws IOException {
        final List<RedisServer> replicas = new ArrayList<>();
        final Set<Integer> replicaPorts = replicasPortsByMainNodePort.get(shard.mainNodePort);
        for (final Integer replicaPort : shard.replicaPorts) {
            replicaPorts.add(replicaPort);
            serverBuilder.reset();
            serverBuilder.port(replicaPort);
            serverBuilder.setting("cluster-enabled yes");
            serverBuilder.setting("cluster-config-file nodes-replica-" + replicaPort + ".conf");
            serverBuilder.setting("cluster-node-timeout 5000");
            serverBuilder.setting("appendonly no");
            final RedisServer slave = serverBuilder.build();
            replicas.add(slave);
        }
        return replicas;
    }

    private RedisServer buildMainNode(final Shard shard) throws IOException {
        replicasPortsByMainNodePort.put(shard.mainNodePort, new HashSet<>());
        serverBuilder.reset();
        serverBuilder.setting("cluster-enabled yes");
        serverBuilder.setting("cluster-config-file nodes-main-" + shard.mainNodePort + ".conf");
        serverBuilder.setting("cluster-node-timeout 5000");
        serverBuilder.setting("appendonly no");
        return serverBuilder.port(shard.mainNodePort).build();
    }
}
