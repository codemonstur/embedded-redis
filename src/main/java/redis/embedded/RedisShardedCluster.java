package redis.embedded;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.embedded.core.RedisShardedClusterBuilder;
import redis.embedded.error.RedisClusterSetupException;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public final class RedisShardedCluster implements Redis {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisShardedCluster.class);

    private static final String CLUSTER_IP = "127.0.0.1";
    private static final int MAX_NUMBER_OF_SLOTS_PER_CLUSTER = 16384;
    private static final Duration SLEEP_DURATION = Duration.ofMillis(300);
    private static final long SLEEP_DURATION_IN_MILLIS = SLEEP_DURATION.toMillis();

    private final List<Redis> servers = new LinkedList<>();
    private final Map<Integer, Set<Integer>> replicasPortsByMainNodePort = new LinkedHashMap<>();
    private final Map<Integer, String> mainNodeIdsByPort = new LinkedHashMap<>();
    private final Duration initializationTimeout;

    public RedisShardedCluster(
            final List<Redis> servers,
            final Map<Integer, Set<Integer>> replicasPortsByMainNodePort,
            final Duration initializationTimeout) {
        this.servers.addAll(servers);
        this.replicasPortsByMainNodePort.putAll(replicasPortsByMainNodePort);
        this.initializationTimeout = initializationTimeout;
    }

    public static RedisShardedClusterBuilder newRedisCluster() { return new RedisShardedClusterBuilder(); }

    @Override
    public boolean isActive() {
        for (final Redis redis : servers) {
            if (!redis.isActive()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void start() throws IOException {
        for (final Redis redis : servers) {
            redis.start();
        }

        linkReplicasAndShards();
    }

    @Override
    public void stop() throws IOException {
        final List<Exception> exceptions = new ArrayList<>();
        for (final Redis redis : servers) {
            stopSafely(redis).ifPresent(exceptions::add);
        }
        if (!exceptions.isEmpty()) {
            throw new IOException("Failed to stop Redis cluster", exceptions.get(0));
        }
    }

    private Optional<Exception> stopSafely(final Redis redis) {
        try {
            redis.stop();
            return Optional.empty();
        } catch (final IOException | RuntimeException e) {
            LOGGER.error("Failed to stop Redis instance", e);
            return Optional.of(e);
        }
    }

    @Override
    public List<Integer> ports() { return new ArrayList<>(serverPorts()); }

    public List<Redis> servers() { return new LinkedList<>(servers); }

    public List<Integer> serverPorts() {
        final List<Integer> ports = new ArrayList<>();
        for (final Redis redis : servers) {
            ports.addAll(redis.ports());
        }
        return ports;
    }

    public int getPort() { return this.ports().get(0); }

    private void linkReplicasAndShards() {
        try {
            final Set<Integer> mainNodePorts = replicasPortsByMainNodePort.keySet();
            // Use the first node as the target to be met by all other nodes
            final Integer clusterMeetTarget = mainNodePorts.iterator().next();
            meetMainNodes(clusterMeetTarget);
            setupReplicas(clusterMeetTarget);
            waitForClusterToBeInteractReady();
        } catch (final RedisClusterSetupException e) {
            try {
                this.stop();
            } catch (final IOException ex) {
                throw new RuntimeException(ex);
            }
            throw new RuntimeException(e);
        }
    }

    private void meetMainNodes(final Integer clusterMeetTarget) throws RedisClusterSetupException {
        // for every shard meet the main node (except the 1st shard) and add their slots manually
        final List<Integer> shardsMainNodePorts = new LinkedList<>(replicasPortsByMainNodePort.keySet());
        final int slotsPerShard = MAX_NUMBER_OF_SLOTS_PER_CLUSTER / shardsMainNodePorts.size();
        for (int i = 0; i < shardsMainNodePorts.size(); i++) {
            final Integer port = shardsMainNodePorts.get(i);
            final int startSlot = i * slotsPerShard;
            final int endSlot = i == shardsMainNodePorts.size() - 1
                          ? MAX_NUMBER_OF_SLOTS_PER_CLUSTER - 1
                          : startSlot + slotsPerShard - 1;
            try (final Jedis jedis = new Jedis(CLUSTER_IP, port)) {
                if (!port.equals(clusterMeetTarget)) {
                    jedis.clusterMeet(CLUSTER_IP, clusterMeetTarget);
                }

                final String nodeId = jedis.clusterMyId();
                mainNodeIdsByPort.put(port, nodeId);
                jedis.clusterAddSlots(IntStream.range(startSlot, endSlot + 1).toArray());
            } catch (final Exception e) {
                throw new RedisClusterSetupException("Failed creating main node instance at port: " + port, e);
            }
        }
    }

    private void setupReplicas(final Integer clusterMeetTarget) throws RedisClusterSetupException {
        for (final Map.Entry<Integer, Set<Integer>> entry : replicasPortsByMainNodePort.entrySet()) {
            final String mainNodeId = mainNodeIdsByPort.get(entry.getKey());
            final Set<Integer> replicaPorts = entry.getValue();
            for (final Integer replicaPort : replicaPorts) {
                try (final Jedis jedis = new Jedis(CLUSTER_IP, replicaPort)) {
                    jedis.clusterMeet(CLUSTER_IP, clusterMeetTarget);
                    waitForNodeToAppearInCluster(jedis, mainNodeId); // make sure main node visible in cluster
                    jedis.clusterReplicate(mainNodeId);
                    waitForClusterToHaveStatusOK(jedis);
                } catch (final Exception e) {
                    throw new RedisClusterSetupException("Failed adding replica instance at port: " + replicaPort, e);
                }
            }
        }
    }

    private void waitForNodeToAppearInCluster(final Jedis jedis, final String nodeId) throws RedisClusterSetupException {
        final boolean nodeReady = waitForPredicateToPass(() -> jedis.clusterNodes().contains(nodeId));
        if (!nodeReady) { throw new RedisClusterSetupException("Node was not ready before timeout"); }
    }

    private void waitForClusterToHaveStatusOK(final Jedis jedis) throws RedisClusterSetupException {
        final boolean clusterIsReady = waitForPredicateToPass(() -> jedis.clusterInfo().contains("cluster_state:ok"));
        if (!clusterIsReady) { throw new RedisClusterSetupException("Cluster did not have status OK before timeout"); }
    }

    private void waitForClusterToBeInteractReady() throws RedisClusterSetupException {
        final boolean clusterIsReady = waitForPredicateToPass(() -> {
            try (final JedisCluster jc = new JedisCluster(new HostAndPort(CLUSTER_IP, getPort()))) {
                jc.get("someKey");
                return true;
            } catch (final Exception e) {
                // ignore
                return false;
            }
        });
        if (!clusterIsReady) { throw new RedisClusterSetupException("Cluster was not stable before timeout"); }
    }

    private boolean waitForPredicateToPass(final Supplier<Boolean> predicate) throws RedisClusterSetupException {
        final long maxWaitInMillis = initializationTimeout.toMillis();

        int waited = 0;
        boolean result = predicate.get();
        while (!result && waited < maxWaitInMillis) {
            try {
                Thread.sleep(SLEEP_DURATION_IN_MILLIS);
            } catch (final InterruptedException e) {
                throw new RedisClusterSetupException("Interrupted while waiting", e);
            }
            waited += SLEEP_DURATION_IN_MILLIS;
            result = predicate.get();
        }
        return result;
    }
}
