# Architecture

Notes for anyone working on the library itself. Users can safely skip this document —
[Getting started](getting-started.md), [Configuration](configuration.md) and
[Clusters](clusters.md) cover everything you need to *use* it.

## Package layout

| Package | Contents |
|---|---|
| `redis.embedded` | Public API: the `Redis` interface, `RedisInstance` (process lifecycle), `RedisServer`, `RedisSentinel`, `RedisCluster`, `RedisShardedCluster` |
| `redis.embedded.core` | Builders (`RedisServerBuilder`, `RedisSentinelBuilder`, `RedisClusterBuilder`, `RedisShardedClusterBuilder`), `ExecutableProvider`, `PortProvider` |
| `redis.embedded.model` | Platform detection (`OS`, `Architecture`, `OsArchitecture`) and topology descriptions (`ReplicationGroup`, `Shard`) |
| `redis.embedded.error` | `OsArchitectureNotFound`, `UnknownOSName`, `RedisClusterSetupException` |
| `redis.embedded.util` | `IO` (streams, temp files, PATH lookup), `IOSupplier`, `CheckedRunnable` |

Two interfaces carry most of the design:

```java
public interface Redis {              // "a running redis thing"
    boolean isActive();
    void start() throws IOException;
    void stop() throws IOException;
    List<Integer> ports();
}

public interface ExecutableProvider { // "where the binary is"
    File get() throws IOException;
}
```

Everything composes through them: clusters are just lists of `Redis`, and every way of finding a
binary is just an `ExecutableProvider`.

## Process lifecycle (`RedisInstance`)

`RedisServer` and `RedisSentinel` are thin constructors over `RedisInstance`, which owns one child
process and the state around it.

### `start()` — in order

1. **Idempotence guard**: if `active`, return immediately.
2. **Validate the binary**: `args.get(0)` must be a file (`FileNotFoundException` otherwise) and
   executable (`AccessDeniedException` otherwise). Catching this early turns a broken provider into a
   clear error instead of an opaque `ProcessBuilder` failure.
3. **Spawn** with `new ProcessBuilder(args)`, working directory = the executable's parent directory.
4. **Register a shutdown hook** (`RedisInstanceCleaner`) so a JVM exit mid-test does not leave
   processes behind.
5. **Wait for readiness**: read stdout line by line, forwarding each to `soutListener`, until a line
   matches the ready pattern or the deadline passes. The deadline is enforced by reading the stream
   on a daemon thread from a shared pool (`IO.STREAM_READERS`) and timing out on `Future.get`; on
   timeout the stream is closed so that thread stops blocking, and the process is destroyed
   forcibly.
6. **Report a failure with evidence**: if readiness never arrived, kill the process *first*, then
   read stderr to EOF (reading stderr of a live process would block until it exits, turning a failed
   start into a hung build) and throw one `IOException` carrying the ready-pattern message plus both
   captured streams.
7. **Drain both streams** on daemon threads for the lifetime of the process, calling the listeners.
   Redis logs to stdout by default and blocks once the pipe fills, so draining is unconditional even
   when nobody listens.
8. Set `active = true`. Any `IOException` in the sequence removes the shutdown hook again and is
   rethrown wrapped in `Failed to start Redis service`.

On success the stdout reader is deliberately **not** closed: the listener threads take over the same
stream, which is why listeners keep receiving lines long after startup (regression covered by
`RedisServerTest.shouldStreamOutputToListenerAfterReady`).

### `stop()` — in order

1. Unregister the shutdown hook (a stale hook would pin the instance in memory until JVM exit; a
   hook that cannot be removed because the JVM is already shutting down is ignored).
2. No process yet → return. This is what makes `stop()` safe in `finally` blocks for setups that
   failed halfway.
3. Normal path: `destroy()`, then wait up to `stopTimeoutSeconds`. If the process is still alive,
   `destroyForcibly()` and wait again — so the port is free when `stop()` returns. Force path
   (`onShutdownForceStop(true)`): `destroyForcibly()` immediately, then wait.
4. `InterruptedException` → force-kill, restore the interrupt flag, throw
   `Failed to stop redis service`.
5. `finally`: `active = false`.

Both waits are bounded by system properties (60s ready, 15s stop by default) so a binary that never
signals readiness or a wedged process cannot hang a build forever.

### Concurrency

`start()` and `stop()` are `synchronized`; `active` is `volatile` because `isActive()` is read from
other threads. Output listeners may be called from the reader threads and must be thread-safe (a
`CopyOnWriteArrayList` in tests).

### Ready patterns

Defined on `Redis`:

| Instance | Pattern | Why |
|---|---|---|
| Server | `.*[Rr]eady to accept connections.*` | Matches Redis 2.x–6.x wording, capitalised or not |
| Sentinel | `.*Sentinel (runid|ID) is.*` | Redis renamed `runid` to `ID` |

Captured startup output from Redis 2.x/3.x/4.x lives in `src/test/resources/*-startup-output.txt`
and is replayed against these patterns in the tests, so changing a pattern means checking those
files first.

## Config generation

### Servers

`RedisServerBuilder.buildCommandArgs()`:

1. Compose the config fresh on every call — it does **not** mutate the builder: `bind <address>`,
   then accumulated `setting(...)`/`configFile(...)` lines, then `settingForNextBuild(...)` lines.
   The default bind goes first so a later explicit `bind` overrides it.
2. Write it to `Files.createTempFile("embedded-redis-server_" + port, ".conf")`, `deleteOnExit()`.
3. Build the argument list: `executable, confPath, --port, port`, plus `--slaveof host port` when a
   replica was requested.

The per-node `nodeSettings` buffer and `slaveOf` are cleared by `reset()`, which the cluster builders
call between nodes — that separation is why one builder can safely produce every node of a cluster
without one node's settings bleeding into the next.

### Sentinels

`RedisSentinelBuilder` composes its file at `build()` time: user settings, then one block of monitor
lines per replication group (auto-generated unless `configFile(...)` was used, in which case the
given path is passed to Redis untouched), then `bind` and `port`. The auto-generated block is
dropped by `reset()`; user settings never are, so `withSentinelBuilder(...)` configuration reaches
every sentinel of a cluster.

## Executable resolution

`newJarResourceProvider()`-style factories detect the OS/architecture **once, when the factory is
called** (so an `UnknownOSName` fails immediately), capture it in the closure, and on each `get()`:

1. look up the resource path — missing entry → `OsArchitectureNotFound`
2. create a fresh temp directory (`redis-*`), copy the classpath resource into it, `setExecutable(true)`,
   `deleteOnExit()` on both

A fresh directory per `get()` is what gives every node of a cluster its own working directory, so
per-node files (`nodes.conf`, dump.rdb, appendonly files) never collide. Providers that return a
stable path (file, PATH, cached URL) share one working directory instead — which is why sharded
cluster nodes get *per-port* `cluster-config-file` names.

`newCachedUrlProvider` downloads with 60s timeouts into a `.part` file next to the target and moves
it into place atomically (falling back to `REPLACE_EXISTING` when `ATOMIC_MOVE` is unsupported), so
an interrupted download can never poison the cache; the partial file is deleted in a `finally`.

## Port providers

| Factory | Implementation detail |
|---|---|
| `newEphemeralPortProvider()` | `new ServerSocket(0)` with `reuseAddress(false)`, read `getLocalPort()`, close — a deliberate small race before Redis binds it |
| `newEphemeralPortProviderInRedisClusterRange()` | Same loop, rejecting ports above 50000 for the cluster bus (`port + 10000`) |
| `newPredefinedPortProvider(Collection)` | Iterator handed out in order; throws `Ran out of Redis ports` when exhausted |
| `newSequencePortProvider(start)` | `AtomicInteger::getAndIncrement` from a fixed start |

## Cluster internals

### `RedisCluster` (sentinel)

Building resolves topology into two flat lists — sentinels and servers. Every sentinel is configured
to monitor every replication group; the shared `RedisSentinelBuilder`/`RedisServerBuilder` are
`reset()` between nodes.

`start()` runs sentinels first (a server that starts after its monitor can be discovered
immediately) and records what it started; on any failure it stops exactly those instances and
rethrows. `stop()` walks everything and aggregates failures with `addSuppressed(...)` rather than
stopping at the first one, so one wedged process cannot leak the others. Both clusters use the same
pattern — the invariant is *"no half-running topology, ever"*.

### `RedisShardedCluster` (redis cluster)

The object holds the servers plus `replicasPortsByMainNodePort` (shard → replica ports) and the
initialization timeout. `start()`:

1. start all processes (same rollback as above);
2. `meetMainNodes` — every main node does `CLUSTER MEET` to the first shard's main node, records its
   own `CLUSTER MYID`, and claims its slot range (`16384 / shardCount` slots each, the last shard
   taking the remainder) **unless** `cluster_slots_assigned:16384` says a previous run already
   assigned them — that check is what makes a stop/start cycle of the same cluster work instead of
   failing with `Slot X is already busy`;
3. `setupReplicas` — meet each replica, wait until its master's node id appears in `CLUSTER NODES`,
   `CLUSTER REPLICATE`, then wait for `cluster_state:ok`;
4. `waitForClusterToBeInteractReady` — a real `JedisCluster` round trip against the first port, the
   only check that proves the cluster is usable rather than merely configured.

Steps 2–4 poll every 300ms until `initializationTimeout` (20s default). Any failure stops the whole
cluster and is rethrown as `RuntimeException(RedisClusterSetupException)` — a checked exception in
an unchecked wrapper, because `start()` only declares `IOException` via the `Redis` interface.

The cluster address is hard-coded to `127.0.0.1`, which is also why shard ports must stay below
50000 (cluster bus = node port + 10000).

## Design invariants

If you touch the code, keep these true — they are what the tests guard:

1. **No orphan processes.** Partial starts roll back; a JVM shutdown hook covers the rest.
2. **No hung threads.** Every wait on a child process or a stream has a deadline; readers run on
   daemon threads; stdout/stderr are always drained (a full pipe would block Redis).
3. **No hung builds.** `stop()` on a never-started instance is a no-op; failures during teardown are
   aggregated, not swallowed.
4. **Ports are free when `stop()` returns**, which is what makes restart tests and parallel suites
   possible.
5. **Builders are reusable and shareable.** Config is composed per build; only per-node state is
   cleared by `reset()`.
6. **Temp artefacts clean themselves up.** Binaries, config files and downloads are `deleteOnExit()`
   or atomically moved into place.

## Build, test, release

- **Toolchain**: Maven 3.6.3+, `maven.compiler.release=8`. `mvn clean package` builds the jar plus
  sources and javadoc jars.
- **Tests**: JUnit 4 + Jedis (`src/test/java`). Useful targets:
  - `make test` — `mvn test`
  - `make coverage` — tests with JaCoCo, report in `target/site/jacoco/index.html`, summary printed
    by `src/build/CoverageSummary.java`
  - `make validate` — dependency updates, CVE scan and license rules (Maven profiles
    `vulnerabilities` / `licenses`)
- **Release**: bump `<version>` in `pom.xml`, then
  1. `make release-notes` — writes `src/docs/releases/release-<version>.txt` from the git log since
     the previous tag
  2. `make deploy` — `mvn clean deploy` to Maven Central, creates the GitHub release with that notes
     file, tags and pushes
- **ARM binaries**: `make build-arm` (see [`compile/`](compile/) and
  [Executables and binaries](executables.md#compiling-your-own-binary)).
