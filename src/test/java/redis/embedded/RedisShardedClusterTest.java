package redis.embedded;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static redis.embedded.RedisShardedCluster.CLUSTER_IP;
import static redis.embedded.RedisShardedCluster.newRedisCluster;

public class RedisShardedClusterTest {
	private RedisShardedCluster cluster;

    @Before
    public void setUp() throws IOException {
		cluster = newRedisCluster()
		   .shard("master1", 1)
		   .shard("master2", 1)
		   .shard("master3", 1)
		   .build();
		cluster.start();
    }

    @Test
    public void testSimpleOperationsAfterClusterStart() {
        try (final JedisCluster jc = new JedisCluster(new HostAndPort(CLUSTER_IP, cluster.getPort()))) {
            jc.set("somekey", "somevalue");
            assertEquals("the value should be equal", "somevalue", jc.get("somekey"));
        }
    }

	@Test
	public void testSimpleOperationsAfterClusterWithEphemeralPortsStart() throws IOException {
		cluster.stop();
		cluster = newRedisCluster().ephemeral()
			 .shard("master1", 1)
			 .shard("master2", 1)
			 .shard("master3", 1)
			 .build();
		cluster.start();
		try (final JedisCluster jc = new JedisCluster(new HostAndPort(CLUSTER_IP, cluster.getPort()))) {
			jc.set("somekey", "somevalue");
			assertEquals("the value should be equal", "somevalue", jc.get("somekey"));
		}
	}

    @After
    public void tearDown() throws Exception {
        cluster.stop();
    }

}
