package redis.embedded;

import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.embedded.core.ExecutableProvider;
import redis.embedded.model.OsArchitecture;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static redis.embedded.RedisServer.SERVER_READY_PATTERN;
import static redis.embedded.RedisServer.newRedisServer;
import static redis.embedded.core.ExecutableProvider.newJarResourceProvider;
import static redis.embedded.model.Architecture.*;
import static redis.embedded.model.OS.*;
import static redis.embedded.model.OsArchitecture.*;

public class RedisServerTest {

	private RedisServer redisServer;

	@Test(timeout = 1500L)
	public void testSimpleRun() throws Exception {
		redisServer = new RedisServer(6381);
		redisServer.start();
		Thread.sleep(1000L);
		redisServer.stop();
	}

	@Test
	public void shouldAllowMultipleRunsWithoutStop() throws IOException {
		try {
			redisServer = new RedisServer(6381);
			redisServer.start();
			redisServer.start();
		} finally {
			redisServer.stop();
		}
	}

	@Test
	public void shouldAllowSubsequentRuns() throws IOException {
		redisServer = new RedisServer(6381);
		redisServer.start();
		redisServer.stop();

		redisServer.start();
		redisServer.stop();

		redisServer.start();
		redisServer.stop();
	}

	@Test
	public void testSimpleOperationsAfterRun() throws IOException {
		redisServer = new RedisServer(6381);
		redisServer.start();

		try (final JedisPool pool = new JedisPool("localhost", 6381);
             final Jedis jedis = pool.getResource()) {
			jedis.mset("abc", "1", "def", "2");

			assertEquals("1", jedis.mget("abc").get(0));
			assertEquals("2", jedis.mget("def").get(0));
			assertNull(jedis.mget("xyz").get(0));
		} finally {
			redisServer.stop();
		}
	}

    @Test
    public void shouldIndicateInactiveBeforeStart() throws IOException {
        redisServer = new RedisServer(6381);
        assertFalse(redisServer.isActive());
    }

    @Test
    public void shouldIndicateActiveAfterStart() throws IOException {
        redisServer = new RedisServer(6381);
        redisServer.start();
        assertTrue(redisServer.isActive());
        redisServer.stop();
    }

    @Test
    public void shouldIndicateInactiveAfterStop() throws IOException {
        redisServer = new RedisServer(6381);
        redisServer.start();
        redisServer.stop();
        assertFalse(redisServer.isActive());
    }

    @Test
    public void shouldOverrideDefaultExecutable() throws IOException {
		final Map<OsArchitecture, String> map = new HashMap<>();
		map.put(UNIX_x86, "/redis-server-6.2.7-linux-386");
		map.put(UNIX_x86_64, "/redis-server-6.2.6-v5-linux-amd64");
		map.put(UNIX_AARCH64, "/redis-server-6.2.7-linux-arm64");
		map.put(WINDOWS_x86_64, "/redis-server-5.0.14.1-windows-amd64.exe");
		map.put(MAC_OS_X_x86_64, "/redis-server-6.2.6-v5-darwin-amd64");
		map.put(MAC_OS_X_ARM64, "/redis-server-6.2.6-v5-darwin-arm64");

        redisServer = newRedisServer()
			.executableProvider(newJarResourceProvider(map))
			.build();
    }

    @Test(expected = FileNotFoundException.class)
    public void shouldFailWhenBadExecutableGiven() throws IOException {
		final Map<OsArchitecture, String> buggyMap = new HashMap<>();
		buggyMap.put(UNIX_x86, "some");
		buggyMap.put(UNIX_x86_64, "some");
		buggyMap.put(UNIX_AARCH64, "some");
		buggyMap.put(WINDOWS_x86, "some");
		buggyMap.put(WINDOWS_x86_64, "some");
		buggyMap.put(MAC_OS_X_x86_64, "some");
		buggyMap.put(MAC_OS_X_ARM64, "some");

        redisServer = newRedisServer()
			.executableProvider(newJarResourceProvider(buggyMap))
			.build();
    }

	@Test
	public void testAwaitRedisServerReady() throws IOException {
		testReadyPattern("/redis-2.x-standalone-startup-output.txt", SERVER_READY_PATTERN);
        testReadyPattern("/redis-3.x-standalone-startup-output.txt", SERVER_READY_PATTERN);
        testReadyPattern("/redis-4.x-standalone-startup-output.txt", SERVER_READY_PATTERN);
	}

	private static void testReadyPattern(final String resourcePath, final Pattern readyPattern) throws IOException {
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
