package redis.embedded;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.embedded.core.RedisSentinelBuilder;
import redis.embedded.util.JedisUtil;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static redis.embedded.RedisCluster.newRedisCluster;
import static redis.embedded.util.JedisUtil.awaitMasterDiscovery;

public class RedisClusterTest {
    private static final String MASTER_NAME = "ourmaster";

    private final RedisSentinelBuilder sentinelBuilder = RedisSentinel.newRedisSentinel();
    private String bindAddress;

    private FakeRedis sentinel1;
    private FakeRedis sentinel2;
    private FakeRedis master1;
    private FakeRedis master2;

    private RedisCluster instance;

    @BeforeEach
    public void setUp() throws UnknownHostException {
        sentinel1 = new FakeRedis();
        sentinel2 = new FakeRedis();
        master1 = new FakeRedis();
        master2 = new FakeRedis();

        // Jedis translates ”localhost” to getLocalHost().getHostAddress() (see Jedis HostAndPort#getLocalHostQuietly),
        // which can vary from 127.0.0.1 (most notably, Debian/Ubuntu return 127.0.1.1)
        if (bindAddress == null) {
            bindAddress = Inet4Address.getLocalHost().getHostAddress();
            sentinelBuilder.bind(bindAddress);
        }
    }

    @AfterEach
    public void tearDown() throws IOException {
        // stop() is a no-op on nodes that never started, so a partially started cluster is fully cleaned up
        if (instance != null) {
            instance.stop();
        }
    }

    @Test
    public void stopShouldStopEntireCluster() throws IOException {
        instance = new RedisCluster(Arrays.<Redis>asList(sentinel1, sentinel2), Arrays.<Redis>asList(master1, master2));

        instance.stop();

        assertTrue(sentinel1.stopped(), "sentinel1 should have been stopped");
        assertTrue(sentinel2.stopped(), "sentinel2 should have been stopped");
        assertTrue(master1.stopped(), "master1 should have been stopped");
        assertTrue(master2.stopped(), "master2 should have been stopped");
    }

    @Test
    public void startShouldStartEntireCluster() throws IOException {
        instance = new RedisCluster(Arrays.<Redis>asList(sentinel1, sentinel2), Arrays.<Redis>asList(master1, master2));

        instance.start();

        assertTrue(sentinel1.started(), "sentinel1 should have been started");
        assertTrue(sentinel2.started(), "sentinel2 should have been started");
        assertTrue(master1.started(), "master1 should have been started");
        assertTrue(master2.started(), "master2 should have been started");
    }

    @Test
    public void isActiveShouldCheckEntireClusterIfAllActive() {
        sentinel1.setActive(true);
        sentinel2.setActive(true);
        master1.setActive(true);
        master2.setActive(true);
        instance = new RedisCluster(Arrays.asList(sentinel1, sentinel2), Arrays.asList(master1, master2));

        assertTrue(instance.isActive());

        master2.setActive(false);

        assertFalse(instance.isActive());
    }

    @Test
    public void testSimpleOperationsAfterRunWithSingleMasterNoSlavesCluster() throws IOException {
        final RedisCluster cluster = newRedisCluster()
            .withSentinelBuilder(sentinelBuilder)
            .ephemeral()
            .sentinelCount(1)
            .replicationGroup(MASTER_NAME, 0)
            .build();
        testPoolAgainstCluster(cluster);
    }

    @Test
    public void testSimpleOperationsAfterRunWithSingleMasterAndOneSlave() throws IOException {
        final RedisCluster cluster = newRedisCluster()
            .withSentinelBuilder(sentinelBuilder)
            .ephemeral()
            .sentinelCount(1)
            .replicationGroup(MASTER_NAME, 1)
            .build();
        testPoolAgainstCluster(cluster);
    }

    @Test
    public void testSimpleOperationsAfterRunWithSingleMasterMultipleSlaves() throws IOException {
        final RedisCluster cluster = newRedisCluster()
            .withSentinelBuilder(sentinelBuilder)
            .ephemeral()
            .sentinelCount(1)
            .replicationGroup(MASTER_NAME, 2)
            .build();
        testPoolAgainstCluster(cluster);
    }

    @Test
    public void testSimpleOperationsAfterRunWithTwoSentinelsSingleMasterMultipleSlaves() throws IOException {
        final RedisCluster cluster = newRedisCluster()
            .withSentinelBuilder(sentinelBuilder)
            .ephemeral()
            .sentinelCount(2)
            .replicationGroup(MASTER_NAME, 2)
            .build();
        testPoolAgainstCluster(cluster);
    }

    @Test
    public void testSimpleOperationsAfterRunWithTwoPredefinedSentinelsSingleMasterMultipleSlaves() throws IOException {
        final List<Integer> sentinelPorts = Arrays.asList(JedisUtil.newFreePort(), JedisUtil.newFreePort());
        final RedisCluster cluster = newRedisCluster()
                .withSentinelBuilder(sentinelBuilder)
                .sentinelPorts(sentinelPorts)
                .ephemeralServers()
                .replicationGroup(MASTER_NAME, 2)
                .build();
        testPoolAgainstCluster(cluster);
    }

    // Regression test: settings passed via withSentinelBuilder used to be silently discarded, because the
    // cluster builder reset the builder's configuration before building every sentinel
    @Test
    public void testCustomSettingsFromSentinelBuilderAreApplied() throws IOException {
        final String password = "cluster-secret";
        final RedisCluster cluster = newRedisCluster()
                .withSentinelBuilder(sentinelBuilder.setting("requirepass " + password))
                .ephemeral()
                .sentinelCount(1)
                .replicationGroup(MASTER_NAME, 0)
                .build();
        final int sentinelPort = cluster.sentinelPorts().get(0);
        try {
            cluster.start();
            try (final Jedis sentinel = new Jedis(bindAddress, sentinelPort)) {
                try {
                    sentinel.ping();
                    fail("Sentinel should require the configured password");
                } catch (final JedisDataException e) {
                    assertTrue(e.getMessage().contains("NOAUTH"),
                            "expected a NOAUTH error but got: " + e.getMessage());
                }
                sentinel.auth(password);
                assertEquals("PONG", sentinel.ping());
            }
        } finally {
            cluster.stop();
        }
    }

    @Test
    public void testSimpleOperationsAfterRunWithThreeSentinelsThreeMastersOneSlavePerMasterCluster() throws IOException {
        final String master1 = "master1";
        final String master2 = "master2";
        final String master3 = "master3";
        final RedisCluster cluster = newRedisCluster()
                .withSentinelBuilder(sentinelBuilder)
                .ephemeral()
                .sentinelCount(3)
                .quorumSize(2)
                .replicationGroup(master1, 1)
                .replicationGroup(master2, 1)
                .replicationGroup(master3, 1)
                .build();
        testPoolAgainstCluster(cluster, master1, master2, master3);
    }

    @Test
    public void testSimpleOperationsAfterRunWithThreeSentinelsThreeMastersOneSlavePerMasterEphemeralCluster() throws IOException {
        final String master1 = "master1";
        final String master2 = "master2";
        final String master3 = "master3";
        final RedisCluster cluster = newRedisCluster().withSentinelBuilder(sentinelBuilder)
                .ephemeral().sentinelCount(3).quorumSize(2)
                .replicationGroup(master1, 1)
                .replicationGroup(master2, 1)
                .replicationGroup(master3, 1)
                .build();
        testPoolAgainstCluster(cluster, master1, master2, master3);
    }

    /**
     * Starts the cluster, waits until the sentinels have discovered the given masters, and runs simple
     * operations against a pool for each master. The cluster is always stopped again, even when the
     * start fails halfway through.
     */
    private void testPoolAgainstCluster(final RedisCluster cluster, final String... masterNames) throws IOException {
        final Set<String> sentinelHosts = JedisUtil.sentinelHosts(cluster);
        final List<JedisSentinelPool> pools = new ArrayList<>();
        final List<Jedis> jedses = new ArrayList<>();
        try {
            cluster.start();

            final String[] masters = masterNames.length == 0 ? new String[] { MASTER_NAME } : masterNames;
            for (final String master : masters) {
                awaitMasterDiscovery(master, sentinelHosts);

                final JedisSentinelPool pool = JedisUtil.newSentinelPool(master, sentinelHosts);
                pools.add(pool);
                jedses.add(testPool(pool));
            }
        } finally {
            for (final Jedis jedis : jedses) {
                jedis.close();
            }
            for (final JedisSentinelPool pool : pools) {
                pool.destroy();
            }
            cluster.stop();
        }
    }

    private Jedis testPool(final JedisSentinelPool pool) {
        final Jedis jedis = pool.getResource();
        jedis.mset("abc", "1", "def", "2");

        assertEquals("1", jedis.mget("abc").get(0));
        assertEquals("2", jedis.mget("def").get(0));
        assertNull(jedis.mget("xyz").get(0));
        return jedis;
    }

    /** Minimal in-memory stand-in for a redis process; replaces the Mockito mocks that needed the (fragile) inline mock-maker. */
    private static final class FakeRedis implements Redis {
        private final Set<String> calls = new HashSet<>();
        private volatile boolean active;

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void start() {
            calls.add("start");
            active = true;
        }

        @Override
        public void stop() {
            calls.add("stop");
            active = false;
        }

        @Override
        public List<Integer> ports() {
            return Collections.emptyList();
        }

        boolean started() {
            return calls.contains("start");
        }

        boolean stopped() {
            return calls.contains("stop");
        }

        void setActive(final boolean active) {
            this.active = active;
        }
    }

}
