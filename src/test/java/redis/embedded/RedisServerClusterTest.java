package redis.embedded;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static redis.embedded.RedisServer.newRedisServer;

public class RedisServerClusterTest {

    private RedisServer redisServer1;
    private RedisServer redisServer2;

    @Before
    public void setUp() throws IOException {
        redisServer1 = newRedisServer().port(6300).build();
        redisServer2 = newRedisServer()
                .port(6301)
                .slaveOf("localhost", 6300)
                .build();

        redisServer1.start();
        redisServer2.start();
    }

    @Test
    public void testSimpleOperationsAfterRun() {
        JedisPool pool = null;
        Jedis jedis = null;
        try {
            pool = new JedisPool("localhost", 6300);
            jedis = pool.getResource();
            jedis.mset("abc", "1", "def", "2");

            assertEquals("1", jedis.mget("abc").get(0));
            assertEquals("2", jedis.mget("def").get(0));
            assertNull(jedis.mget("xyz").get(0));
        } finally {
            if (jedis != null)
                pool.returnResource(jedis);
        }
    }


    @After
    public void tearDown() throws IOException {
        redisServer1.stop();
        redisServer2.stop();
    }
}
