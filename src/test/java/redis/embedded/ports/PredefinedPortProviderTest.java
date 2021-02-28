package redis.embedded.ports;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static redis.embedded.PortProviders.newPredefinedPortProvider;

public class PredefinedPortProviderTest {

    @Test
    public void nextShouldGiveNextPortFromAssignedList() {
        //given
        final Collection<Integer> ports = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        final Supplier<Integer> provider = newPredefinedPortProvider(ports);

        //when
        final List<Integer> returnedPorts = new ArrayList<Integer>();
        for (int i = 0; i < ports.size(); i++) {
            returnedPorts.add(provider.get());
        }

        //then
        assertEquals(ports, returnedPorts);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nextShouldThrowExceptionWhenRunOutsOfPorts() {
        //given
        final Collection<Integer> ports = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        final Supplier<Integer> provider = newPredefinedPortProvider(ports);

        //when
        for (int i = 0; i < ports.size(); i++) {
            provider.get();
        }

        //then exception should be thrown...
        provider.get();
    }

}