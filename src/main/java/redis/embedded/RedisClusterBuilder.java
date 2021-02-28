package redis.embedded;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import static redis.embedded.PortProviders.*;

public final class RedisClusterBuilder {

    private RedisSentinelBuilder sentinelBuilder = new RedisSentinelBuilder();
    private RedisServerBuilder serverBuilder = new RedisServerBuilder();
    private int sentinelCount = 1;
    private int quorumSize = 1;
    private Supplier<Integer> sentinelPortProvider = newSequencePortProvider(26379);
    private Supplier<Integer> replicationGroupPortProvider = newSequencePortProvider(6379);
    private final List<ReplicationGroup> groups = new LinkedList<ReplicationGroup>();

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
        this.groups.add(new ReplicationGroup(masterName, slaveCount, this.replicationGroupPortProvider));
        return this;
    }

    public RedisCluster build() {
        final List<Redis> sentinels = buildSentinels();
        final List<Redis> servers = buildServers();
        return new RedisCluster(sentinels, servers);
    }

    private List<Redis> buildServers() {
        final List<Redis> servers = new ArrayList<Redis>();
        for (final ReplicationGroup g : groups) {
            servers.add(buildMaster(g));
            buildSlaves(servers, g);
        }
        return servers;
    }

    private void buildSlaves(final List<Redis> servers, ReplicationGroup g) {
        for (final Integer slavePort : g.slavePorts) {
            serverBuilder.reset();
            serverBuilder.port(slavePort);
            serverBuilder.slaveOf("localhost", g.masterPort);
            final RedisServer slave = serverBuilder.build();
            servers.add(slave);
        }
    }

    private Redis buildMaster(final ReplicationGroup g) {
        serverBuilder.reset();
        return serverBuilder.port(g.masterPort).build();
    }

    private List<Redis> buildSentinels() {
        int toBuild = this.sentinelCount;
        final List<Redis> sentinels = new LinkedList<>();
        while (toBuild-- > 0) {
            sentinels.add(buildSentinel());
        }
        return sentinels;
    }

    private Redis buildSentinel() {
        sentinelBuilder.reset();
        sentinelBuilder.port(nextSentinelPort());
        for (final ReplicationGroup g : groups) {
            sentinelBuilder.masterName(g.masterName);
            sentinelBuilder.masterPort(g.masterPort);
            sentinelBuilder.quorumSize(quorumSize);
            sentinelBuilder.addDefaultReplicationGroup();
        }
        return sentinelBuilder.build();
    }

    private int nextSentinelPort() {
        return sentinelPortProvider.get();
    }

    private static class ReplicationGroup {
        private final String masterName;
        private final int masterPort;
        private final List<Integer> slavePorts = new LinkedList<>();

        private ReplicationGroup(final String masterName, int slaveCount, final Supplier<Integer> portProvider) {
            this.masterName = masterName;
            masterPort = portProvider.get();
            while (slaveCount-- > 0) {
                slavePorts.add(portProvider.get());
            }
        }
    }
}
