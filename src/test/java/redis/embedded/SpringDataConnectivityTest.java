package redis.embedded;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class SpringDataConnectivityTest {

    private RedisServer redisServer;
    private RedisTemplate<String, String> template;
    private JedisConnectionFactory connectionFactory;

    @Before
    public void setUp() throws IOException {
        redisServer = new RedisServer(6381);
        redisServer.start();

        connectionFactory = new JedisConnectionFactory();
        connectionFactory.getStandaloneConfiguration().setHostName("localhost");
        connectionFactory.getStandaloneConfiguration().setPort(6381);
        connectionFactory.afterPropertiesSet();

        template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.afterPropertiesSet();
    }

    @Test
    public void shouldBeAbleToUseSpringData() {
        template.opsForValue().set("foo", "bar");

        String result = template.opsForValue().get("foo");

        assertEquals("bar", result);
    }

    @After
    public void tearDown() throws IOException {
        redisServer.stop();
    }

}
