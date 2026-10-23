package redis.embedded;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.embedded.util.JedisUtil;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static redis.embedded.RedisServer.newRedisServer;

public class RedisServerClusterTest {

    private RedisServer redisServer1;
    private RedisServer redisServer2;

    private int masterPort;

    @BeforeEach
    public void setUp() throws IOException {
        masterPort = JedisUtil.newFreePort();
        final int slavePort = JedisUtil.newFreePort();

        redisServer1 = newRedisServer().port(masterPort).build();
        redisServer2 = newRedisServer().port(slavePort)
                .slaveOf("localhost", masterPort)
                .build();

        try {
            redisServer1.start();
            redisServer2.start();
        } catch (final IOException e) {
            // stop() is a no-op on instances that never started, so nothing that started is left running
            tearDown();
            throw e;
        }
    }

    @Test
    public void testSimpleOperationsAfterRun() {
        JedisPool pool = null;
        Jedis jedis = null;
        try {
            pool = JedisUtil.newPool(masterPort);
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
        }
    }


    @AfterEach
    public void tearDown() throws IOException {
        redisServer1.stop();
        redisServer2.stop();
    }

}
