package redis.embedded.model;

import redis.embedded.core.PortProvider;

import java.util.LinkedList;
import java.util.List;

/**
 * The {@code Shard} class.
 */
public final class Shard {

    /**
     * The {@code name} field.
     */
    public final String name;
    /**
     * The {@code mainNodePort} field.
     */
    public final int mainNodePort;
    /**
     * The {@code replicaPorts} field.
     */
    public final List<Integer> replicaPorts = new LinkedList<>();

    /**
     * Creates a new instance.
     *
     * @param name the name
     * @param replicaCount the replica count
     * @param provider the provider
     */
    public Shard(final String name, int replicaCount, final PortProvider provider) {
        this.name = name;
        this.mainNodePort = provider.get();
        while (replicaCount-- > 0) {
            this.replicaPorts.add(provider.get());
        }
    }

}
