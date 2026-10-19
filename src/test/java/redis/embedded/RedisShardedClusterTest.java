package redis.embedded;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.embedded.util.JedisUtil;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static redis.embedded.RedisShardedCluster.newRedisCluster;

public class RedisShardedClusterTest {
	private RedisShardedCluster cluster;

    @Before
    public void setUp() throws IOException {
		cluster = newCluster();
		cluster.start();
    }

    @Test
    public void testSimpleOperationsAfterClusterStart() {
        try (final JedisCluster jc = JedisUtil.newCluster(new HostAndPort("127.0.0.1", cluster.getPort()))) {
            jc.set("somekey", "somevalue");
            assertEquals("the value should be equal", "somevalue", jc.get("somekey"));
        }
    }

	@Test
	public void testSimpleOperationsAfterClusterWithEphemeralPortsStart() throws IOException {
		cluster.stop();
		cluster = newCluster();
		cluster.start();
		try (final JedisCluster jc = JedisUtil.newCluster(new HostAndPort("127.0.0.1", cluster.getPort()))) {
			jc.set("somekey", "somevalue");
			assertEquals("the value should be equal", "somevalue", jc.get("somekey"));
		}
	}

	// Regression test: a restarted cluster restored its slot assignments from nodes.conf, and the setup
	// then failed with 'Slot X is already busy'
	@Test(timeout = 120_000L)
	public void testRestartOfTheSameCluster() throws IOException {
		cluster.stop();
		cluster.start();
		try (final JedisCluster jc = JedisUtil.newCluster(new HostAndPort("127.0.0.1", cluster.getPort()))) {
			jc.set("somekey", "somevalue");
			assertEquals("the value should be equal", "somevalue", jc.get("somekey"));
		}
	}

    private static RedisShardedCluster newCluster() throws IOException {
        return newRedisCluster().ephemeral()
             .shard("master1", 1)
             .shard("master2", 1)
             .shard("master3", 1)
             .build();
    }

    @After
    public void tearDown() throws Exception {
        if (cluster != null) {
            cluster.stop();
        }
    }

}
