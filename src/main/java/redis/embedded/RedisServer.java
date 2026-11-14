package redis.embedded;

import redis.embedded.core.ExecutableProvider;
import redis.embedded.core.RedisServerBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * The {@code RedisServer} class.
 */
public final class RedisServer extends RedisInstance {

    /**
     * Creates a new instance.
     *
     * @throws IOException if the operation fails
     */
    public RedisServer() throws IOException {
        this(DEFAULT_REDIS_PORT);
    }

    /**
     * Creates a new instance.
     *
     * @param port the port
     * @throws IOException if the operation fails
     */
    public RedisServer(final int port) throws IOException {
        this(port, newRedisServer().port(port).buildCommandArgs(), false);
	}

    /**
     * Creates a new instance.
     *
     * @param port the port
     * @param executable the executable
     */
    public RedisServer(final int port, final File executable) {
        this( port
            , Arrays.asList(executable.getAbsolutePath(), "--port", Integer.toString(port))
            , false
            );
    }

    /**
     * Creates a new instance.
     *
     * @param port the port
     * @param executableProvider the executable provider
     * @throws IOException if the operation fails
     */
    public RedisServer(final int port, final ExecutableProvider executableProvider) throws IOException {
        this( port
            , Arrays.asList(executableProvider.get().getAbsolutePath(), "--port", Integer.toString(port))
            , false
            );
    }

    /**
     * Creates a new instance.
     *
     * @param port the port
     * @param args the command-line arguments
     * @param forceStop the force stop
     */
    public RedisServer(final int port, final List<String> args, final boolean forceStop) {
        super(port, args, SERVER_READY_PATTERN, forceStop, null, null);
    }

    /**
     * Creates a new instance.
     *
     * @param port the port
     * @param args the command-line arguments
     * @param forceStop the force stop
     * @param soutListener the sout listener
     * @param serrListener the serr listener
     */
    public RedisServer(final int port, final List<String> args, final boolean forceStop,
                       final Consumer<String> soutListener, final Consumer<String> serrListener) {
        super(port, args, SERVER_READY_PATTERN, forceStop, soutListener, serrListener);
    }

    /**
     * Returns the result of {@code newRedisServer}.
     *
     * @return the result
     */
    public static RedisServerBuilder newRedisServer() {
        return new RedisServerBuilder();
    }

}
