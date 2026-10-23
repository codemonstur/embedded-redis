package redis.embedded;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;
import redis.embedded.util.JedisUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static redis.embedded.RedisSentinel.SENTINEL_READY_PATTERN;
import static redis.embedded.util.Collections.newHashSet;
import static redis.embedded.util.JedisUtil.awaitMasterDiscovery;

public class RedisSentinelTest {
    private static final String MASTER_NAME = "mymaster";

    private String bindAddress;

    private RedisSentinel sentinel;
    private RedisServer server;

    @BeforeEach
    public void setup() throws Exception {
        // Jedis translates ”localhost” to getLocalHost().getHostAddress() (see Jedis HostAndPort#getLocalHostQuietly),
        // which can vary from 127.0.0.1 (most notably, Debian/Ubuntu return 127.0.1.1)
        if (bindAddress == null) {
            bindAddress = Inet4Address.getLocalHost().getHostAddress();
        }
    }

    @Test
    @Timeout(value = 60, unit = SECONDS)
    public void testSimpleRun() throws Exception {
        server = newRedisServerOnFreePort();
        sentinel = newSentinelOnFreePort(server.ports().get(0));
        sentinel.start();
        server.start();
        server.stop();
        sentinel.stop();
    }

    @Test
    public void shouldAllowSubsequentRuns() throws IOException {
        final int port = JedisUtil.newFreePort();
        sentinel = RedisSentinel.newRedisSentinel().port(port).bind(bindAddress).build();
        sentinel.start();
        sentinel.stop();

        sentinel.start();
        sentinel.stop();

        sentinel.start();
        sentinel.stop();
    }

    @Test
    public void testSimpleOperationsAfterRun() throws IOException {
        server = newRedisServerOnFreePort();
        sentinel = newSentinelOnFreePort(server.ports().get(0));
        server.start();
        sentinel.start();

        final Set<String> sentinelHosts = newHashSet("localhost:" + sentinel.ports().get(0));

        JedisSentinelPool pool = null;
        Jedis jedis = null;
        try {
            // Sentinel discovery of the master is asynchronous; wait for it instead of sleeping for a fixed time
            awaitMasterDiscovery(MASTER_NAME, sentinelHosts);

            pool = JedisUtil.newSentinelPool(MASTER_NAME, sentinelHosts);
            jedis = pool.getResource();
            jedis.mset("abc", "1", "def", "2");

            assertEquals("1", jedis.mget("abc").get(0));
            assertEquals("2", jedis.mget("def").get(0));
            assertNull(jedis.mget("xyz").get(0));
        } finally {
            if (jedis != null) {
                jedis.close();
            }
            if (pool != null) {
                pool.destroy();
            }
            sentinel.stop();
            server.stop();
        }
    }

    @Test
    public void testAwaitRedisSentinelReady() throws Exception {
        assertReadyPattern("/redis-2.x-sentinel-startup-output.txt", SENTINEL_READY_PATTERN);
        assertReadyPattern("/redis-3.x-sentinel-startup-output.txt", SENTINEL_READY_PATTERN);
        assertReadyPattern("/redis-4.x-sentinel-startup-output.txt", SENTINEL_READY_PATTERN);
    }

    private RedisServer newRedisServerOnFreePort() throws IOException {
        return new RedisServer(JedisUtil.newFreePort());
    }

    private RedisSentinel newSentinelOnFreePort(final int masterPort) {
        return RedisSentinel.newRedisSentinel()
                .port(JedisUtil.newFreePort())
                .masterPort(masterPort)
                .bind(bindAddress)
                .build();
    }

    private static void assertReadyPattern(final String resourcePath, final Pattern readyPattern) throws IOException {
        final InputStream in = RedisServerTest.class.getResourceAsStream(resourcePath);
        assertNotNull(in);
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            do {
                line = reader.readLine();
                assertNotNull(line);
            } while (!readyPattern.matcher(line).matches());
        }
    }

}
