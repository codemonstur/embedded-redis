package redis.embedded.ports;

import org.junit.Test;
import redis.embedded.core.PortProvider;

import static org.junit.Assert.assertEquals;
import static redis.embedded.core.PortProvider.newSequencePortProvider;

public class SequencePortProviderTest {

    @Test
    public void nextShouldIncrementPorts() {
        final int startPort = 10;
        final int portCount = 101;
        final PortProvider provider = newSequencePortProvider(startPort);

        int max = 0;
        for (int i = 0; i < portCount; i++) {
            int port = provider.get();
            if (port > max) {
                max = port;
            }
        }

        assertEquals(portCount + startPort - 1, max);
    }

}