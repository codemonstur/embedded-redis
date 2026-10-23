package redis.embedded;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.embedded.util.JedisUtil;

import java.io.IOException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static redis.embedded.RedisShardedCluster.newRedisCluster;

public class RedisShardedClusterTest {
	private RedisShardedCluster cluster;

    @BeforeEach
    public void setUp() throws IOException {
		cluster = newCluster();
		cluster.start();
    }

    @Test
    public void testSimpleOperationsAfterClusterStart() {
        try (final JedisCluster jc = JedisUtil.newCluster(new HostAndPort("127.0.0.1", cluster.getPort()))) {
            jc.set("somekey", "somevalue");
            assertEquals( "somevalue", jc.get("somekey"), "the value should be equal");
        }
    }

	@Test
	public void testSimpleOperationsAfterClusterWithEphemeralPortsStart() throws IOException {
		cluster.stop();
		cluster = newCluster();
		cluster.start();
		try (final JedisCluster jc = JedisUtil.newCluster(new HostAndPort("127.0.0.1", cluster.getPort()))) {
			jc.set("somekey", "somevalue");
			assertEquals("somevalue", jc.get("somekey"), "the value should be equal");
		}
	}

	// Regression test: a restarted cluster restored its slot assignments from nodes.conf, and the setup
	// then failed with 'Slot X is already busy'
	@Test
	@Timeout(value = 120, unit = SECONDS)
	public void testRestartOfTheSameCluster() throws IOException {
		cluster.stop();
		cluster.start();
		try (final JedisCluster jc = JedisUtil.newCluster(new HostAndPort("127.0.0.1", cluster.getPort()))) {
			jc.set("somekey", "somevalue");
			assertEquals("somevalue", jc.get("somekey"), "the value should be equal");
		}
	}

    private static RedisShardedCluster newCluster() throws IOException {
        return newRedisCluster().ephemeral()
             .shard("master1", 1)
             .shard("master2", 1)
             .shard("master3", 1)
             .build();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (cluster != null) {
            cluster.stop();
        }
    }

}
