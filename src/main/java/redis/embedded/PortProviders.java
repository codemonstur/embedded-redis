package redis.embedded;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public enum PortProviders {;

    public static Supplier<Integer> newEphemeralPortProvider() {
        return () -> {
            try (final ServerSocket socket = new ServerSocket(0)) {
                socket.setReuseAddress(false);
                return socket.getLocalPort();
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not provide ephemeral port", e);
            }
        };
    }

    public static Supplier<Integer> newPredefinedPortProvider(final Collection<Integer> ports) {
        final Iterator<Integer> iterator = ports.iterator();
        return () -> {
            if (!iterator.hasNext())
                throw new IllegalArgumentException("Ran out of Redis ports");
            return iterator.next();
        };
    }

    public static Supplier<Integer> newSequencePortProvider() {
        return newSequencePortProvider(26379);
    }
    public static Supplier<Integer> newSequencePortProvider(final int start) {
        final AtomicInteger currentPort = new AtomicInteger(start);
        return currentPort::getAndIncrement;
    }

}
