package redis.embedded.model;

import redis.embedded.core.PortProvider;

import java.util.LinkedList;
import java.util.List;

public final class Shard {

    public final String name;
    public final int mainNodePort;
    public final List<Integer> replicaPorts = new LinkedList<>();

    public Shard(final String name, int replicaCount, final PortProvider provider) {
        this.name = name;
        this.mainNodePort = provider.get();
        while (replicaCount-- > 0) {
            this.replicaPorts.add(provider.get());
        }
    }

}
