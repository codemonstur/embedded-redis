package redis.embedded;

import org.junit.Ignore;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.embedded.core.ExecutableProvider;
import redis.embedded.core.ExecutableProviderBuilder;
import redis.embedded.core.RedisServerBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static redis.embedded.RedisServer.SERVER_READY_PATTERN;
import static redis.embedded.model.Architecture.aarch64;
import static redis.embedded.model.Architecture.x86;
import static redis.embedded.model.Architecture.x86_64;
import static redis.embedded.model.OS.MAC_OS_X;
import static redis.embedded.model.OS.UNIX;
import static redis.embedded.model.OS.WINDOWS;

public class RedisServerTest {

	private RedisServer redisServer;

	@Test(timeout = 1500L)
	public void testSimpleRun() throws Exception {
		redisServer = new RedisServer(6379);
		redisServer.start();
		Thread.sleep(1000L);
		redisServer.stop();
	}

	@Test
	public void shouldAllowMultipleRunsWithoutStop() throws IOException {
		try {
			redisServer = new RedisServer(6379);
			redisServer.start();
			redisServer.start();
		} finally {
			redisServer.stop();
		}
	}

	@Test
	public void shouldAllowSubsequentRuns() throws IOException {
		redisServer = new RedisServer(6379);
		redisServer.start();
		redisServer.stop();

		redisServer.start();
		redisServer.stop();

		redisServer.start();
		redisServer.stop();
	}

	@Test
	public void testSimpleOperationsAfterRun() throws IOException {
		redisServer = new RedisServer(6379);
		redisServer.start();

		try (final JedisPool pool = new JedisPool("localhost", 6379);
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
    public void shouldIndicateInactiveBeforeStart() {
        redisServer = new RedisServer(6379);
        assertFalse(redisServer.isActive());
    }

    @Test
    public void shouldIndicateActiveAfterStart() throws IOException {
        redisServer = new RedisServer(6379);
        redisServer.start();
        assertTrue(redisServer.isActive());
        redisServer.stop();
    }

    @Test
    public void shouldIndicateInactiveAfterStop() throws IOException {
        redisServer = new RedisServer(6379);
        redisServer.start();
        redisServer.stop();
        assertFalse(redisServer.isActive());
    }

	/**
	 * Temporary disabled until deciding what should be the behavior of
	 * {@link ExecutableProvider#newRedis2_8_19Provider()}
	 */
	@Ignore
    @Test
    public void shouldOverrideDefaultExecutable() {
        ExecutableProvider customProvider = new ExecutableProviderBuilder()
                .put(UNIX, x86, "/redis-server-2.8.19-32")
                .put(UNIX, x86_64, "/redis-server-2.8.19")
                .put(WINDOWS, x86, "/redis-server-2.8.19.exe")
                .put(WINDOWS, x86_64, "/redis-server-2.8.19.exe")
                .put(MAC_OS_X, "/redis-server-2.8.19")
                .build();

        redisServer = new RedisServerBuilder()
                .redisExecProvider(customProvider)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailWhenBadExecutableGiven() {
        ExecutableProvider buggyProvider = new ExecutableProviderBuilder()
                .put(UNIX, "some")
                .put(WINDOWS, x86, "some")
                .put(WINDOWS, x86_64, "some")
                .put(MAC_OS_X, "some")
                .build();

        redisServer = new RedisServerBuilder()
                .redisExecProvider(buggyProvider)
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
