package redis.embedded;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RedisServer extends AbstractRedisInstance {
    private static final int DEFAULT_REDIS_PORT = 6379;

    public RedisServer() {
        this(DEFAULT_REDIS_PORT);
    }

    public RedisServer(final int port) {
        this(port, builder().port(port).build().args);
	}

    public RedisServer(File executable, int port) {
        this(port, Arrays.asList(
            executable.getAbsolutePath(),
            "--port", Integer.toString(port)
        ));
    }

    public RedisServer(final RedisExecProvider redisExecProvider, final int port) throws IOException {
        this(port, Arrays.asList(
            redisExecProvider.get().getAbsolutePath(),
            "--port", Integer.toString(port)
        ));
    }

    RedisServer(final int port, final List<String> args) {
        super(port, ".*(R|r)eady to accept connections.*");
        this.args = new ArrayList<String>(args);
    }

    public static RedisServerBuilder builder() {
        return new RedisServerBuilder();
    }

}
