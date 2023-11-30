package redis.embedded.core;

import redis.embedded.Redis;
import redis.embedded.RedisCluster;
import redis.embedded.RedisServer;
import redis.embedded.model.ReplicationGroup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static redis.embedded.core.PortProvider.*;

public final class RedisClusterBuilder {

    private RedisSentinelBuilder sentinelBuilder = new RedisSentinelBuilder();
    private RedisServerBuilder serverBuilder = new RedisServerBuilder();
    private int sentinelCount = 1;
    private int quorumSize = 1;
    private PortProvider sentinelPortProvider = newSequencePortProvider(26379);
    private PortProvider replicationGroupPortProvider = newSequencePortProvider(6379);
    private final List<ReplicationGroup> groups = new LinkedList<>();

    public RedisClusterBuilder withSentinelBuilder(final RedisSentinelBuilder sentinelBuilder) {
        this.sentinelBuilder = sentinelBuilder;
        return this;
    }

    public RedisClusterBuilder withServerBuilder(final RedisServerBuilder serverBuilder) {
        this.serverBuilder = serverBuilder;
        return this;
    }

    public RedisClusterBuilder sentinelPorts(final Collection<Integer> ports) {
        this.sentinelPortProvider = newPredefinedPortProvider(ports);
        this.sentinelCount = ports.size();
        return this;
    }

    public RedisClusterBuilder serverPorts(final Collection<Integer> ports) {
        this.replicationGroupPortProvider = newPredefinedPortProvider(ports);
        return this;
    }

    public RedisClusterBuilder ephemeralSentinels() {
        this.sentinelPortProvider = newEphemeralPortProvider();
        return this;
    }

    public RedisClusterBuilder ephemeralServers() {
        this.replicationGroupPortProvider = newEphemeralPortProvider();
        return this;
    }

    public RedisClusterBuilder ephemeral() {
        ephemeralSentinels();
        ephemeralServers();
        return this;
    }

    public RedisClusterBuilder sentinelCount(final int sentinelCount) {
        this.sentinelCount = sentinelCount;
        return this;
    }

    public RedisClusterBuilder sentinelStartingPort(final int startingPort) {
        this.sentinelPortProvider = newSequencePortProvider(startingPort);
        return this;
    }

    public RedisClusterBuilder quorumSize(final int quorumSize) {
        this.quorumSize = quorumSize;
        return this;
    }

    public RedisClusterBuilder replicationGroup(final String masterName, final int slaveCount) {
        this.groups.add(new ReplicationGroup(masterName, slaveCount, replicationGroupPortProvider));
        return this;
    }

    public RedisCluster build() throws IOException {
        final List<Redis> sentinels = buildSentinels();
        final List<Redis> servers = buildServers();
        return new RedisCluster(sentinels, servers);
    }

    private List<Redis> buildServers() throws IOException {
        final List<Redis> servers = new ArrayList<>();
        for (final ReplicationGroup g : groups) {
            servers.add(buildMaster(g));
            buildSlaves(servers, g);
        }
        return servers;
    }

    private void buildSlaves(final List<Redis> servers, ReplicationGroup g) throws IOException {
        for (final Integer slavePort : g.slavePorts) {
            serverBuilder.reset();
            serverBuilder.port(slavePort);
            serverBuilder.slaveOf("localhost", g.masterPort);
            final RedisServer slave = serverBuilder.build();
            servers.add(slave);
        }
    }

    private Redis buildMaster(final ReplicationGroup g) throws IOException {
        serverBuilder.reset();
        return serverBuilder.port(g.masterPort).build();
    }

    private List<Redis> buildSentinels() {
        int toBuild = sentinelCount;
        final List<Redis> sentinels = new LinkedList<>();
        while (toBuild-- > 0) {
            sentinels.add(buildSentinel());
        }
        return sentinels;
    }

    private Redis buildSentinel() {
        sentinelBuilder.reset();
        sentinelBuilder.port(nextSentinelPort());
        for (final ReplicationGroup group : groups) {
            sentinelBuilder.masterName(group.masterName);
            sentinelBuilder.masterPort(group.masterPort);
            sentinelBuilder.quorumSize(quorumSize);
            sentinelBuilder.addDefaultReplicationGroup();
        }
        return sentinelBuilder.build();
    }

    private int nextSentinelPort() {
        return sentinelPortProvider.get();
    }

}
