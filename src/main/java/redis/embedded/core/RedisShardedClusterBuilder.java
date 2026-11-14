package redis.embedded.core;

import redis.embedded.Redis;
import redis.embedded.RedisServer;
import redis.embedded.RedisShardedCluster;
import redis.embedded.model.Shard;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

import static redis.embedded.core.PortProvider.*;

/**
 * The {@code RedisShardedClusterBuilder} class.
 */
public final class RedisShardedClusterBuilder {

    private static final Duration DEFAULT_INITIALIZATION_TIMEOUT = Duration.ofSeconds(20);

    private RedisServerBuilder serverBuilder = new RedisServerBuilder();
    private PortProvider shardPortProvider = newSequencePortProvider(6379);
    private Duration initializationTimeout = DEFAULT_INITIALIZATION_TIMEOUT;
    private final List<Shard> shards = new LinkedList<>();
    private final Map<Integer, Set<Integer>> replicasPortsByMainNodePort = new LinkedHashMap<>();

    /**
     * Returns the result of {@code withServerBuilder}.
     *
     * @param serverBuilder the server builder
     * @return the result
     */
    public RedisShardedClusterBuilder withServerBuilder(final RedisServerBuilder serverBuilder) {
        this.serverBuilder = serverBuilder;
        return this;
    }

    /**
     * Returns the result of {@code serverPorts}.
     *
     * @param ports the ports
     * @return the result
     */
    public RedisShardedClusterBuilder serverPorts(final Collection<Integer> ports) {
        this.shardPortProvider = newPredefinedPortProvider(ports);
        return this;
    }

    /**
     * Returns the result of {@code initializationTimeout}.
     *
     * @param initializationTimeout the initialization timeout
     * @return the result
     */
    public RedisShardedClusterBuilder initializationTimeout(final Duration initializationTimeout) {
        this.initializationTimeout = initializationTimeout;
        return this;
    }

    /**
     * Returns the result of {@code ephemeralServers}.
     *
     * @return the result
     */
    public RedisShardedClusterBuilder ephemeralServers() {
        this.shardPortProvider = newEphemeralPortProviderInRedisClusterRange();
        return this;
    }

    /**
     * Returns the result of {@code ephemeral}.
     *
     * @return the result
     */
    public RedisShardedClusterBuilder ephemeral() {
        ephemeralServers();
        return this;
    }
    /**
     * Returns the result of {@code shard}.
     *
     * @param name the name
     * @param replicaCount the replica count
     * @return the result
     */
    public RedisShardedClusterBuilder shard(final String name, final int replicaCount) {
        this.shards.add(new Shard(name, replicaCount, this.shardPortProvider));
        return this;
    }

    /**
     * Returns the result of {@code build}.
     *
     * @return the result
     * @throws IOException if the operation fails
     */
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
            serverBuilder.settingForNextBuild("cluster-enabled yes");
            serverBuilder.settingForNextBuild("cluster-config-file nodes-replica-" + replicaPort + ".conf");
            serverBuilder.settingForNextBuild("cluster-node-timeout 5000");
            serverBuilder.settingForNextBuild("appendonly no");
            final RedisServer slave = serverBuilder.build();
            replicas.add(slave);
        }
        return replicas;
    }

    private RedisServer buildMainNode(final Shard shard) throws IOException {
        replicasPortsByMainNodePort.put(shard.mainNodePort, new HashSet<>());
        serverBuilder.reset();
        serverBuilder.settingForNextBuild("cluster-enabled yes");
        serverBuilder.settingForNextBuild("cluster-config-file nodes-main-" + shard.mainNodePort + ".conf");
        serverBuilder.settingForNextBuild("cluster-node-timeout 5000");
        serverBuilder.settingForNextBuild("appendonly no");
        return serverBuilder.port(shard.mainNodePort).build();
    }


    /**
     * Creates a new instance.
     */
    public RedisShardedClusterBuilder() {
    }
}
