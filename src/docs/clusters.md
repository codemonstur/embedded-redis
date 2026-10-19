# Clusters

Two ready-made topologies are available, both implementing the same
[`Redis`](../main/java/redis/embedded/Redis.java) interface as a single server:

| Class | Topology | Use when |
|---|---|---|
| [`RedisCluster`](../main/java/redis/embedded/RedisCluster.java) | Redis Sentinel: N sentinels monitoring M master/replica groups | You need failover and master discovery, like production with `JedisSentinelPool` |
| [`RedisShardedCluster`](../main/java/redis/embedded/RedisShardedCluster.java) | Redis Cluster: sharded slots with replicas | Your application is cluster-aware (`JedisCluster`) and spreads keys across nodes |

A plain master/replica pair needs no special class — build two servers and call
`.slaveOf(host, port)` on the second one (see
[`RedisServerClusterTest`](../test/java/redis/embedded/RedisServerClusterTest.java)).

## Choosing ports

Both cluster builders share the port strategies from
[`PortProvider`](../main/java/redis/embedded/core/PortProvider.java):

| Strategy | How to ask for it | Behaviour |
|---|---|---|
| Sequence | default, or `sentinelStartingPort(int)` / default server provider | Increments from a fixed start (26379 for sentinels, 6379 for servers) |
| Predefined | `sentinelPorts(...)` / `serverPorts(...)` | Hands out exactly the ports you pass, in order; throws `Ran out of Redis ports` when exhausted |
| Ephemeral | `ephemeralSentinels()`, `ephemeralServers()`, `ephemeral()` | Asks the OS for a free port per node |
| Ephemeral, cluster range | `ephemeralServers()` on the sharded builder | Same, but restricted to ports below 50000 |

Use ephemeral ports unless something external pins your ports. Fixed sequence ports collide as soon
as two test classes (or two build agents) run at the same time.

Ports are handed out when you *declare* a node — `replicationGroup(...)` and `shard(...)` allocate
one from the provider immediately — not when `build()` runs. With an ephemeral provider that reserves
a free port a moment before Redis binds it, which is normally exactly what you want.

The sharded variant keeps ports below `REDIS_CLUSTER_MAX_PORT_EXCLUSIVE = 50000` because Redis opens
the cluster bus on *node port + 10000*; 50000 leaves a safe margin below the 65535 limit. Setting
`cluster-port` explicitly would be the alternative, but that config currently makes Redis fail on
Windows.

## Sentinel cluster

```java
import redis.embedded.RedisCluster;
import redis.embedded.core.RedisSentinelBuilder;
import redis.embedded.RedisSentinel;

RedisSentinelBuilder sentinelBuilder = RedisSentinel.newRedisSentinel();
sentinelBuilder.bind(bindAddress);

RedisCluster cluster = RedisCluster.newRedisCluster()
    .withSentinelBuilder(sentinelBuilder)
    .ephemeralServers()
    .sentinelStartingPort(26400)
    .sentinelCount(3)
    .quorumSize(2)
    .replicationGroup("master1", 1)   // 1 master + 1 replica
    .replicationGroup("master2", 1)
    .replicationGroup("master3", 1)
    .build();

cluster.start();
try {
    // JedisSentinelPool pool = new JedisSentinelPool("master1", sentinelHosts);
} finally {
    cluster.stop();
}
```

### Builder options

| Method | Description | Default |
|---|---|---|
| `withSentinelBuilder(RedisSentinelBuilder)` | Template for every sentinel (bind, password, listeners, executable, …) | new default builder |
| `withServerBuilder(RedisServerBuilder)` | Template for every server | new default builder |
| `sentinelCount(int)` | Number of sentinels | `1` |
| `sentinelStartingPort(int)` | First port of the sentinel sequence | `26379` |
| `sentinelPorts(Collection<Integer>)` | Explicit sentinel ports (also sets the count) | — |
| `serverPorts(Collection<Integer>)` | Explicit ports for the servers, in group order | — |
| `ephemeralSentinels()` / `ephemeralServers()` / `ephemeral()` | Free ports per node | — |
| `quorumSize(int)` | Quorum written into every sentinel's config | `1` |
| `replicationGroup(String masterName, int slaveCount)` | Adds a monitored master plus `slaveCount` replicas; call once per group | none |
| `build()` | Creates the cluster (no processes started yet) | — |

Each group's master takes the next port from the server port provider, each replica the one after
it. Every sentinel monitors every group.

### Lifecycle and failure handling

1. `start()` starts all sentinels first, then the servers.
2. If any node fails to start, everything that did start is stopped again before the error is
   rethrown — no orphan processes keep ports bound.
3. `stop()` stops every node, even if one of them fails; the first failure is thrown with the others
   attached as suppressed exceptions.
4. `stop()` on a cluster that never started is a no-op, so it is safe in a `finally` block.

### Inspecting the cluster

```java
List<Integer> all = cluster.ports();
List<Integer> sentinelPorts = cluster.sentinelPorts();
List<Integer> serverPorts = cluster.serverPorts();
List<Redis> sentinels = cluster.sentinels();
List<Redis> servers = cluster.servers();
boolean up = cluster.isActive();   // true only when every node is running
```

### Custom settings reach every node

Whatever you configure on the shared builders is applied to each node the cluster builds:

```java
RedisCluster cluster = RedisCluster.newRedisCluster()
    .withSentinelBuilder(RedisSentinel.newRedisSentinel().setting("requirepass " + password))
    .withServerBuilder(RedisServer.newRedisServer().setting("appendonly yes"))
    .ephemeral()
    .sentinelCount(1)
    .replicationGroup("ourmaster", 0)
    .build();
```

The cluster builder resets per-node state (`slaveOf`, generated monitor lines,
`settingForNextBuild`) between nodes, so nothing leaks from one node into the next while your
`setting(...)` lines are preserved. See [Configuration](configuration.md#reusing-and-sharing-builders).

### Bind address caveat

Sentinel announces the address it is bound to. Clients that resolve `localhost` may not land on the
same address — Jedis resolves it through `Inet4Address.getLocalHost()`, which on Debian/Ubuntu is
`127.0.1.1` rather than `127.0.0.1`. In tests, bind explicitly:

```java
String bindAddress = Inet4Address.getLocalHost().getHostAddress();
sentinelBuilder.bind(bindAddress);
```

Master discovery through sentinels is asynchronous: wait for it (poll `SENTINEL masters`) instead of
sleeping a fixed amount of time before opening your pool.

## Sharded cluster

```java
import redis.embedded.RedisShardedCluster;

RedisShardedCluster cluster = RedisShardedCluster.newRedisCluster()
    .ephemeral()
    .shard("master1", 1)   // 1 main node + 1 replica
    .shard("master2", 1)
    .shard("master3", 1)
    .build();

cluster.start();
try (JedisCluster jc = new JedisCluster(new HostAndPort("127.0.0.1", cluster.getPort()))) {
    jc.set("somekey", "somevalue");
} finally {
    cluster.stop();
}
```

### Builder options

| Method | Description | Default |
|---|---|---|
| `withServerBuilder(RedisServerBuilder)` | Template for every node | new default builder |
| `shard(String name, int replicaCount)` | Adds a shard: a main node plus `replicaCount` replicas | none |
| `serverPorts(Collection<Integer>)` | Explicit ports, in shard order | — |
| `ephemeralServers()` / `ephemeral()` | Free ports below 50000 | sequence from `6379` |
| `initializationTimeout(Duration)` | How long `start()` waits for the topology to come together | `20s` |
| `build()` | Creates the cluster | — |

Every node automatically gets `cluster-enabled yes`, a per-node `cluster-config-file`,
`cluster-node-timeout 5000` and `appendonly no`.

### What `start()` does

1. Starts every node (rolling back on failure, like `RedisCluster`).
2. `CLUSTER MEET`: all main nodes join the first shard's main node.
3. Assigns the 16384 hash slots evenly across the main nodes — the last shard gets the remainder.
   If the slots are already assigned (a restarted cluster restored them from `nodes.conf`) the step
   is skipped, so restarts work.
4. Meets each replica into the cluster, waits for its master to be visible, then
   `CLUSTER REPLICATE <master-id>`.
5. Waits until `cluster_state:ok`, and finally until a `JedisCluster` round-trip succeeds.

If the topology does not come together within `initializationTimeout`, the cluster is stopped and a
`RuntimeException` wrapping `RedisClusterSetupException` is thrown, with the reason ("Cluster did
not have status OK before timeout", "Failed adding replica instance at port: …", …) as its cause.

### Inspecting the cluster

```java
List<Integer> ports = cluster.serverPorts();
int anyPort = cluster.getPort();     // first server port, handy for JedisCluster
List<Redis> nodes = cluster.servers();
boolean up = cluster.isActive();
```

## Parallel test suites

Each cluster instance owns its processes, so two suites can run side by side as long as their ports
do not overlap — which `ephemeral()` guarantees. Two suites using the default sequence ports will
collide, and a start failure will surface as `Address already in use` in the captured stderr (see
[Troubleshooting](troubleshooting.md)).
