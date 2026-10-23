package redis.embedded.ports;

import org.junit.jupiter.api.Test;
import redis.embedded.core.PortProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    public void nextShouldThrowExceptionWhenRunOutsOfPorts() {
        final Collection<Integer> ports = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        final PortProvider provider = newPredefinedPortProvider(ports);

        for (int i = 0; i < ports.size(); i++) {
            provider.get();
        }

        assertThrows(IllegalArgumentException.class, provider::get);
    }

}