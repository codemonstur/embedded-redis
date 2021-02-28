package redis.embedded.model;

import redis.embedded.core.PortProvider;

import java.util.LinkedList;
import java.util.List;

public final class ReplicationGroup {
    public final String masterName;
    public final int masterPort;
    public final List<Integer> slavePorts = new LinkedList<>();

    public ReplicationGroup(final String masterName, int slaveCount, final PortProvider provider) {
        this.masterName = masterName;
        this.masterPort = provider.get();
        while (slaveCount-- > 0) {
            this.slavePorts.add(provider.get());
        }
    }
}
