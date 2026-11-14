package redis.embedded.model;

import redis.embedded.core.PortProvider;

import java.util.LinkedList;
import java.util.List;

/**
 * The {@code ReplicationGroup} class.
 */
public final class ReplicationGroup {

    /**
     * The {@code masterName} field.
     */
    public final String masterName;
    /**
     * The {@code masterPort} field.
     */
    public final int masterPort;
    /**
     * The {@code slavePorts} field.
     */
    public final List<Integer> slavePorts = new LinkedList<>();

    /**
     * Creates a new instance.
     *
     * @param masterName the master name
     * @param slaveCount the slave count
     * @param provider the provider
     */
    public ReplicationGroup(final String masterName, int slaveCount, final PortProvider provider) {
        this.masterName = masterName;
        this.masterPort = provider.get();
        while (slaveCount-- > 0) {
            this.slavePorts.add(provider.get());
        }
    }

}
