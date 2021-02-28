package redis.embedded.ports;

import org.junit.Test;
import redis.embedded.core.PortProvider;

import static org.junit.Assert.assertEquals;
import static redis.embedded.core.PortProvider.newSequencePortProvider;

public class SequencePortProviderTest {

    @Test
    public void nextShouldIncrementPorts() {
        //given
        final int startPort = 10;
        final int portCount = 101;
        final PortProvider provider = newSequencePortProvider(startPort);

        //when
        int max = 0;
        for (int i = 0; i < portCount; i++) {
            int port = provider.get();
            if (port > max) {
                max = port;
            }
        }

        //then
        assertEquals(portCount + startPort - 1, max);
    }

}