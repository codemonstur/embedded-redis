package redis.embedded.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@code PortProvider} interface.
 */
public interface PortProvider {
    // Redis uses a cluster bus port as the first node port + 10000, so we need to make sure we use ports lower than
    // 55535 to ensure we always get a valid cluster bus port. We chose 50000 in order to have a safe margin.
    // Theoretically we could use the "cluster-port" as documented here:
    // https://redis.io/docs/reference/cluster-spec/#the-cluster-bus
    // however adding that config to the redis server currently results in an error when running on Windows.
    /**
     * The {@code REDIS_CLUSTER_MAX_PORT_EXCLUSIVE} field.
     */
    int REDIS_CLUSTER_MAX_PORT_EXCLUSIVE = 50000;

    /**
     * Returns the result of {@code get}.
     *
     * @return the result
     */
    int get();

    /**
     * Returns the result of {@code newEphemeralPortProvider}.
     *
     * @return the result
     */
    static PortProvider newEphemeralPortProvider() {
        return () -> {
            try (final ServerSocket socket = new ServerSocket(0)) {
                socket.setReuseAddress(false);
                return socket.getLocalPort();
            } catch (final IOException e) {
                throw new IllegalArgumentException("Could not provide ephemeral port", e);
            }
        };
    }

    /**
     * Returns the result of {@code newEphemeralPortProviderInRedisClusterRange}.
     *
     * @return the result
     */
    static PortProvider newEphemeralPortProviderInRedisClusterRange() {
        final PortProvider ephemeralPortProvider = newEphemeralPortProvider();
        return () -> {
            int port = ephemeralPortProvider.get();
            while (port > REDIS_CLUSTER_MAX_PORT_EXCLUSIVE) { port = ephemeralPortProvider.get();}
            return port;
        };
    }

    /**
     * Returns the result of {@code newPredefinedPortProvider}.
     *
     * @param ports the ports
     * @return the result
     */
    static PortProvider newPredefinedPortProvider(final Collection<Integer> ports) {
        final Iterator<Integer> iterator = ports.iterator();
        return () -> {
            if (!iterator.hasNext())
                throw new IllegalArgumentException("Ran out of Redis ports");
            return iterator.next();
        };
    }

    /**
     * Returns the result of {@code newSequencePortProvider}.
     *
     * @return the result
     */
    static PortProvider newSequencePortProvider() {
        return newSequencePortProvider(26379);
    }

    /**
     * Returns the result of {@code newSequencePortProvider}.
     *
     * @param start the start
     * @return the result
     */
    static PortProvider newSequencePortProvider(final int start) {
        final AtomicInteger currentPort = new AtomicInteger(start);
        return currentPort::getAndIncrement;
    }

}
