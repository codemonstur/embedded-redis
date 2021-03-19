package redis.embedded.ports;

import org.junit.Test;
import redis.embedded.core.PortProvider;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static redis.embedded.core.PortProvider.newEphemeralPortProvider;

public class EphemeralPortProviderTest {

    @Test
    public void nextShouldGiveNextFreeEphemeralPort() {
        final int portCount = 20;
        final PortProvider provider = newEphemeralPortProvider();

        final List<Integer> ports = new ArrayList<>();
        for (int i = 0; i < portCount; i++) {
            ports.add(provider.get());
        }

        assertEquals(portCount, ports.size());
    }

}
