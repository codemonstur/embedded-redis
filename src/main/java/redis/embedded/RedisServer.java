package redis.embedded;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class RedisServer extends RedisInstance {
    public static final int DEFAULT_REDIS_PORT = 6379;
    public static final Pattern SERVER_READY_PATTERN = Pattern.compile(".*[Rr]eady to accept connections.*");

    public RedisServer() {
        this(DEFAULT_REDIS_PORT);
    }

    public RedisServer(final int port) {
        this(port, newRedisServer().port(port).buildCommandArgs());
	}

    public RedisServer(final int port, final File executable) {
        this(port, Arrays.asList(
            executable.getAbsolutePath(),
            "--port", Integer.toString(port)
        ));
    }

    public RedisServer(final int port, final RedisExecProvider redisExecProvider) throws IOException {
        this(port, Arrays.asList(
            redisExecProvider.get().getAbsolutePath(),
            "--port", Integer.toString(port)
        ));
    }

    RedisServer(final int port, final List<String> args) {
        super(port, args, SERVER_READY_PATTERN);
    }

    public static RedisServerBuilder newRedisServer() {
        return new RedisServerBuilder();
    }

}
