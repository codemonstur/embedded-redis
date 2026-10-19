# Configuration

There are two entry points for configuration:

- `RedisServer.newRedisServer()` returns a [`RedisServerBuilder`](../main/java/redis/embedded/core/RedisServerBuilder.java)
- `RedisSentinel.newRedisSentinel()` returns a [`RedisSentinelBuilder`](../main/java/redis/embedded/core/RedisSentinelBuilder.java)

Both builders compose a `redis.conf` in a temporary file and a command line, then hand them to the
process. Neither builder is thread-safe; build your instances on one thread.

## How a server is configured

`RedisServerBuilder.buildCommandArgs()` composes the configuration in this order:

1. `bind <bindAddress>` — always first, so an explicit `bind` line you add later wins
   (Redis uses the last occurrence of a directive)
2. everything passed to `setting(...)`, `settingIf(...)` or `configFile(...)`, in call order
3. everything passed to `settingForNextBuild(...)`, in call order

The result is written to a temp file named `embedded-redis-server_<port>*.conf` (marked
`deleteOnExit()`), and the process is started as:

```
<executable> <config-file> --port <port> [--slaveof <host> <port>]
```

Because the config file is generated for every build, a builder can be reused: `build()` twice and
you get two independent instances.

> The working directory of the child process is the directory containing the executable. Relative
> paths inside your configuration (for example `cluster-config-file` or `dir`) resolve against it.

## RedisServerBuilder reference

| Method | Description | Default |
|---|---|---|
| `executableProvider(ExecutableProvider)` | Where the binary comes from | jar resource for the current OS/arch |
| `bind(String)` | Adds the `bind` line | `127.0.0.1` |
| `port(int)` | Listening port (`--port` and `port` argument) | `6379` |
| `slaveOf(String host, int port)` / `slaveOf(InetSocketAddress)` | Adds `--slaveof`, turning the server into a replica | none |
| `configFile(String path)` / `configFile(Path)` | Appends the contents of an existing `redis.conf` | none |
| `setting(String line)` | Appends one config directive | none |
| `settingIf(boolean, String)` | Same as `setting`, but only when the condition is true | — |
| `settingForNextBuild(String line)` | Appends a directive for the **next** `build()` only (dropped by `reset()`) | none |
| `onShutdownForceStop(boolean)` | `true` kills the process with `destroyForcibly()` instead of the normal signal | `false` |
| `soutListener(Consumer<String>)` | Called for every stdout line, from startup onwards | none (output discarded) |
| `serrListener(Consumer<String>)` | Called for every stderr line | none (output discarded) |
| `build()` | Creates the `RedisServer` (extracts the binary, writes the config) | — |
| `buildCommandArgs()` | Only the command line; used by the `RedisServer` convenience constructors | — |
| `reset()` | Clears `slaveOf` and `settingForNextBuild` lines | — |

### Example

```java
RedisServer redis = RedisServer.newRedisServer()
    .port(6379)
    .setting("bind 127.0.0.1")       // prevents security popups on Windows
    .setting("daemonize no")
    .setting("appendonly no")
    .setting("maxmemory 128M")
    .settingIf(needsAuth, "requirepass secret")
    .build();
```

### Three flavours of `setting`

| Method | Lifetime | Use it for |
|---|---|---|
| `setting(...)` | Accumulates; applies to every instance built from this builder | Options shared by all nodes of a cluster, or a single long-lived server |
| `settingForNextBuild(...)` | Applied once, dropped by `reset()` | Per-node options when one builder builds many nodes |
| `configFile(...)` | File contents appended at build time | Reusing an existing `redis.conf` |

Cluster builders call `reset()` between nodes, so a `settingForNextBuild` line never leaks from one
node into the next. If you pass a shared builder into a cluster (`withServerBuilder(...)`),
`setting(...)` lines apply to every node while `settingForNextBuild(...)` and `slaveOf(...)` are
reset per node.

### Raw command lines

For full control there is a constructor that takes the arguments verbatim:

```java
List<String> args = Arrays.asList("/usr/bin/redis-server", "--port", "6380",
                                 "--maxmemory", "128mb", "--save", "");
RedisServer redis = new RedisServer(6380, args, false);
```

The first element must be the path of an existing, executable file — `start()` verifies both.
`RedisServerBuilder.buildCommandArgs()` is the supported way to obtain such a list when you want the
builder's config generation but not its `RedisServer` instance.

## RedisSentinelBuilder reference

| Method | Description | Default |
|---|---|---|
| `executableProvider(ExecutableProvider)` | Where the binary comes from | jar resource for the current OS/arch |
| `bind(String)` | Adds the `bind` line | `127.0.0.1` |
| `port(Integer)` | Sentinel port (`--port` and a `port` line) | `26379` |
| `masterName(String)` | Name of the monitored master | `mymaster` |
| `masterPort(Integer)` | Port of the monitored master | `6379` |
| `quorumSize(int)` | `sentinel quorum` | `1` |
| `downAfterMilliseconds(long)` | `sentinel down-after-milliseconds` | `60000` |
| `failOverTimeout(long)` | `sentinel failover-timeout` | `180000` |
| `parallelSyncs(int)` | `sentinel parallel-syncs` | `1` |
| `configFile(String path)` | Use an existing sentinel config file **as-is** | none |
| `setting(String line)` | Appends one config directive | none |
| `settingIf(boolean, String)` | Conditional variant of `setting` | — |
| `onShutdownForceStop(boolean)` | Force-kill on shutdown instead of a signal | `false` |
| `soutListener` / `serrListener` | Per-line output listeners | none |
| `build()` | Creates the `RedisSentinel` | — |
| `reset()` | Drops the auto-generated monitor lines | — |
| `addDefaultReplicationGroup()` | Appends the monitor lines for the current master settings | — |

Unless you supply a config file, the generated file is:

```
<your settings, if any>
sentinel monitor <masterName> 127.0.0.1 <masterPort> <quorumSize>
sentinel down-after-milliseconds <masterName> <downAfterMilliseconds>
sentinel failover-timeout <masterName> <failOverTimeout>
sentinel parallel-syncs <masterName> <parallelSyncs>
bind <bind>
port <port>
```

and the process is started as `<executable> <config-file> --sentinel --port <port>`.

Two rules to be aware of:

- `configFile(...)` and `setting(...)` are mutually exclusive; mixing them throws
  `IllegalArgumentException`.
- With `configFile(...)` the file is passed to Redis untouched — no `bind`, `port`, or monitor lines
  are appended, so the file has to contain everything Redis needs.

### Example

```java
RedisSentinel sentinel = RedisSentinel.newRedisSentinel()
    .port(26400)
    .bind(bindAddress)
    .masterName("ourmaster")
    .masterPort(6379)
    .quorumSize(2)
    .downAfterMilliseconds(5000)
    .setting("requirepass secret")
    .build();
sentinel.start();
```

## Reusing and sharing builders

`RedisClusterBuilder` and `RedisShardedClusterBuilder` create many nodes from one server builder and
call `reset()` between them. What survives a reset:

| Builder | Cleared by `reset()` | Kept |
|---|---|---|
| `RedisServerBuilder` | `slaveOf(...)`, `settingForNextBuild(...)` | `bind`, `port`, `setting(...)`, `configFile(...)`, listeners, executable provider |
| `RedisSentinelBuilder` | generated monitor lines | your `setting(...)`/`configFile(...)`, bind, port, timers, quorum |

That is why settings passed through `withSentinelBuilder(...)` or `withServerBuilder(...)` reach
every node of the cluster, while per-node state (which port is a replica of which master) does not
leak between nodes.

## Output listeners

Redis logs to stdout by default. Both streams are drained continuously — without a listener the
lines are simply discarded — and a listener receives every line, including those printed long after
startup:

```java
RedisServer redis = RedisServer.newRedisServer()
    .port(port)
    .soutListener(line -> log.info("redis: {}", line))
    .serrListener(line -> log.warn("redis: {}", line))
    .build();
```

Listeners are also the easiest way to see *why* a startup failed; the exception thrown by `start()`
already embeds the captured stdout and stderr.
