package redis.embedded.ports;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static redis.embedded.PortProviders.newEphemeralPortProvider;

public class EphemeralPortProviderTest {

    @Test
    public void nextShouldGiveNextFreeEphemeralPort() {
        //given
        final int portCount = 20;
        final Supplier<Integer> provider = newEphemeralPortProvider();

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