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
        //given
        final int portCount = 20;
        final PortProvider provider = newEphemeralPortProvider();

        //when
        final List<Integer> ports = new ArrayList<Integer>();
        for (int i = 0; i < portCount; i++) {
            ports.add(provider.get());
        }

        //then
        System.out.println(ports);
        assertEquals(portCount, ports.size());
    }

}