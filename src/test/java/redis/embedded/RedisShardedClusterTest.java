package redis.embedded;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.embedded.core.ExecutableProvider;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static redis.embedded.RedisShardedCluster.newRedisCluster;
import static redis.embedded.core.ExecutableProvider.newJarResourceProvider;

public class RedisShardedClusterTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

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
        try (final JedisCluster jc = new JedisCluster(new HostAndPort("127.0.0.1", cluster.getPort()))) {
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
		try (final JedisCluster jc = new JedisCluster(new HostAndPort("127.0.0.1", cluster.getPort()))) {
			jc.set("somekey", "somevalue");
			assertEquals("the value should be equal", "somevalue", jc.get("somekey"));
		}
	}

	@Test
	public void shouldAllowSubsequentRuns() throws IOException {
		cluster.stop();
		cluster.start();
		try (final JedisCluster jc = new JedisCluster(new HostAndPort("127.0.0.1", cluster.getPort()))) {
			jc.set("somekey", "somevalue");
			assertEquals("the value should be equal", "somevalue", jc.get("somekey"));
		}
	}

	@Test
	public void shouldAllowSubsequentRunsInSameDirectory() throws IOException {
		cluster.stop();
		//
		File folder = temporaryFolder.newFolder();
		cluster = newRedisCluster()
				.withServerBuilder(RedisServer
						.newRedisServer()
						.executableProvider(newJarResourceProvider(folder)))
				.shard("master1", 1)
				.shard("master2", 1)
				.shard("master3", 1)
				.build();
		cluster.start();
		cluster.stop();
		cluster.start();
		try (final JedisCluster jc = new JedisCluster(new HostAndPort("127.0.0.1", cluster.getPort()))) {
			jc.set("somekey", "somevalue");
			assertEquals("the value should be equal", "somevalue", jc.get("somekey"));
		}
	}

    @After
    public void tearDown() throws Exception {
        cluster.stop();
    }

}
