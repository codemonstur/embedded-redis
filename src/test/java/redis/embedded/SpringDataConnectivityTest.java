package redis.embedded;

import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.util.JedisUtil;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SpringDataConnectivityTest {

    private RedisServer redisServer;
    private RedisTemplate<String, String> template;
    private JedisConnectionFactory connectionFactory;

    private int port;

    @BeforeEach
    public void setUp() throws IOException {
        port = JedisUtil.newFreePort();
        redisServer = new RedisServer(port);
        redisServer.start();

        connectionFactory = new JedisConnectionFactory();
        connectionFactory.getStandaloneConfiguration().setHostName("localhost");
        connectionFactory.getStandaloneConfiguration().setPort(port);
        connectionFactory.afterPropertiesSet();

        template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.afterPropertiesSet();
    }

    @Test
    public void shouldBeAbleToUseSpringData() {
        template.opsForValue().set("foo", "bar");

        final String result = template.opsForValue().get("foo");

        assertEquals("bar", result);
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Releases the pooled connections first, otherwise they can still hold the port when the server stops
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisServer != null) {
            redisServer.stop();
        }
    }

}
