# Getting started

embedded-redis starts a real `redis-server` process for your JVM tests, waits until it is ready, and
shuts it down again when the test is done. There is no in-memory reimplementation: your code talks to
a genuine Redis over the network, so behaviour matches production (replication, persistence, cluster
slots, and so on).

## Requirements

- Java 8 or newer (the library is compiled with `maven.compiler.release=8`)
- Maven 3.6.3+ if you build the library itself

## Dependency

```xml
<dependency>
  <groupId>com.github.codemonstur</groupId>
  <artifactId>embedded-redis</artifactId>
  <version>1.4.4</version>
</dependency>
```

The library depends on [Jedis](https://github.com/redis/jedis) at compile scope, so it arrives
transitively. embedded-redis only needs Jedis itself to configure a sharded cluster; for everything
else you are free to use whichever Redis client you like.

## Your first server

```java
import redis.embedded.RedisServer;

RedisServer redis = new RedisServer(6379);
redis.start();          // blocks until the server prints its ready-line
// ... run your queries against 127.0.0.1:6379 ...
redis.stop();           // waits for the process to exit
```

`new RedisServer(6379)` picks the bundled binary for the current OS and architecture (see
[Executables and binaries](executables.md)), writes a temporary `redis.conf`, and starts the process
with that configuration.

If you do not care about the port, `new RedisServer()` uses the default Redis port 6379.

## The lifecycle contract

Every embedded instance implements [`redis.embedded.Redis`](../main/java/redis/embedded/Redis.java):

```java
public interface Redis {
    boolean isActive();
    void start() throws IOException;
    void stop() throws IOException;
    List<Integer> ports();
}
```

- `start()` blocks until Redis prints its ready-line (`Ready to accept connections` for a server,
  `Sentinel ID is` for a sentinel). No sleeps or polling are needed afterwards: when `start()`
  returns, the instance accepts connections.
- `start()` is idempotent — calling it on an already active instance does nothing.
- Instances can be started again after `stop()`, which is handy for restart tests.
- `isActive()` reports whether the instance is currently running.
- `stop()` waits for the process to exit so that the port is free again when it returns. It is a
  no-op on an instance that never started, so a partially started setup can always be torn down
  safely.
- Both calls are bounded in time; see [Troubleshooting](troubleshooting.md) for the timeout system
  properties.

## A complete JUnit test

```java
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.Assert.assertEquals;

public class RedisTest {

    private RedisServer redis;
    private int port;

    @Before
    public void setUp() throws IOException {
        port = freePort();
        redis = new RedisServer(port);
        redis.start();
    }

    @After
    public void tearDown() throws IOException {
        redis.stop();   // safe even if setUp() failed halfway
    }

    @Test
    public void storesAndRetrievesValues() {
        try (Jedis jedis = new Jedis("127.0.0.1", port)) {
            assertEquals("OK", jedis.set("abc", "1"));
            assertEquals("1", jedis.get("abc"));
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
```

### Why a free port?

Fixed ports break as soon as two test classes (or two build agents on one machine) run in parallel.
Asking the OS for an ephemeral port with `new ServerSocket(0)` avoids the clash. The port is released
when the socket closes, and Redis binds it a moment later — an acceptable, conventional race for
tests. The library's own tests use this approach, and cluster builders have built-in support for it
(`ephemeral()`, see [Clusters](clusters.md)).

## Configuring a server

The fluent builder exposes everything the simple constructor hides:

```java
RedisServer redis = RedisServer.newRedisServer()
    .port(6379)
    .bind("127.0.0.1")
    .setting("maxmemory 128M")
    .setting("appendonly no")
    .slaveOf("localhost", 6380)     // optional: replicate from another instance
    .soutListener(System.out::println)
    .build();
redis.start();
```

Full option reference: [Configuration](configuration.md).

## Connecting a sentinel, replication, or a cluster

- Standalone master/replica: build two servers, the second with `.slaveOf(host, port)` —
  see [`RedisServerClusterTest`](../test/java/redis/embedded/RedisServerClusterTest.java).
- Master managed by sentinels: `RedisSentinel` or `RedisCluster`.
- Sharded (Redis Cluster) topology: `RedisShardedCluster`.

Both are described in [Clusters](clusters.md).

## Where to go next

- [Configuration](configuration.md) — every builder option and how config files are generated
- [Executables and binaries](executables.md) — using your own Redis build, other platforms, TLS
- [Clusters](clusters.md) — sentinel and sharded topologies on ephemeral ports
- [Troubleshooting](troubleshooting.md) — what to do when a start or stop fails
