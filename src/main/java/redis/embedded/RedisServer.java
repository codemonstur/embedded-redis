package redis.embedded;

import redis.embedded.core.ExecutableProvider;
import redis.embedded.core.RedisServerBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public final class RedisServer extends RedisInstance {

    public RedisServer() throws IOException {
        this(DEFAULT_REDIS_PORT);
    }

    public RedisServer(final int port) throws IOException {
        this(port, newRedisServer().port(port).buildCommandArgs(), false);
	}

    public RedisServer(final int port, final File executable) {
        this( port
            , Arrays.asList(executable.getAbsolutePath(), "--port", Integer.toString(port))
            , false
            );
    }

    public RedisServer(final int port, final ExecutableProvider executableProvider) throws IOException {
        this( port
            , Arrays.asList(executableProvider.get().getAbsolutePath(), "--port", Integer.toString(port))
            , false
            );
    }

    public RedisServer(final int port, final List<String> args, final boolean forceStop) {
        super(port, args, SERVER_READY_PATTERN, forceStop);
    }

    public static RedisServerBuilder newRedisServer() {
        return new RedisServerBuilder();
    }

}
