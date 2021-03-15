package redis.embedded.ports;

import org.junit.Test;
import redis.embedded.core.PortProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static redis.embedded.core.PortProvider.newPredefinedPortProvider;

public class PredefinedPortProviderTest {

    @Test
    public void nextShouldGiveNextPortFromAssignedList() {
        final Collection<Integer> ports = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        final PortProvider provider = newPredefinedPortProvider(ports);

        final List<Integer> returnedPorts = new ArrayList<Integer>();
        for (int i = 0; i < ports.size(); i++) {
            returnedPorts.add(provider.get());
        }

        assertEquals(ports, returnedPorts);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nextShouldThrowExceptionWhenRunOutsOfPorts() {
        final Collection<Integer> ports = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        final PortProvider provider = newPredefinedPortProvider(ports);

        for (int i = 0; i < ports.size(); i++) {
            provider.get();
        }

        provider.get();
    }

}