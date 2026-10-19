# Troubleshooting

## Read the exception first

`start()` never fails silently. When a process does not come up, it throws an `IOException` that
contains everything known so far:

```
Redis-server process appears not to have started. It did not print '.*[Rr]eady to accept connections.*'
within 60 seconds. standard-out contains this: <everything Redis printed>
standard-err contains this: <everything Redis printed to stderr>
```

Most startup failures — missing shared libraries, a port that is taken, a bad config line — are
reported by Redis on stderr, so the answer is usually already in the exception. To keep watching the
output after startup, attach listeners (see
[Configuration](configuration.md#output-listeners)):

```java
RedisServer redis = RedisServer.newRedisServer()
    .port(port)
    .soutListener(line -> log.info("redis: {}", line))
    .serrListener(line -> log.error("redis: {}", line))
    .build();
```

## Timeouts

Two waits are bounded so that a broken binary cannot hang a build forever:

| System property | Bounds | Default |
|---|---|---|
| `-Dembedded-redis.readyTimeoutSeconds=N` | how long `start()` waits for the ready-line | `60` |
| `-Dembedded-redis.stopTimeoutSeconds=N` | how long `stop()` waits for the process to exit before killing it | `15` |

Both properties are read once, when `RedisInstance` is loaded, so they must be set at JVM start —
a `System.setProperty(...)` in test setup is too late if the class was already loaded. Put them on
the command line of the JVM that runs the tests, for example through Surefire:

```bash
mvn test -DargLine="-Dembedded-redis.readyTimeoutSeconds=120"
```

or in your IDE's run configuration / `junit.platform` config.

When `stop()` exceeds its budget the process is destroyed forcibly; `stop()` then still waits for it
to die so the port is free again when the call returns. If you prefer to skip the graceful phase
entirely, build with `.onShutdownForceStop(true)`.

Raise `readyTimeoutSeconds` on slow CI machines (cold caches, antivirus scanning a freshly extracted
binary) and whenever you start many nodes in one `start()` call — a cluster's nodes start
sequentially, each with its own full timeout budget.

## Symptom → cause → fix

### `Failed to start Redis service` … `Redis binary … could not be found`

The provider returned a path that does not exist (anymore). Typical causes:

- the binary was extracted to a temp directory that got cleaned up between build and start
- a `newEnvironmentVariableProvider()` / `newSystemPropertyProvider()` pointed at a stale path

Point the provider at a stable location, or use `newCachedUrlProvider(...)`/`newExecutableInPath(...)`
which resolve the file when `get()` is called.

### `Redis binary … is not executable`

The file exists but has no execute bit (common after copying a binary into a Docker image, or after
an antivirus tool re-written it) — `chmod +x <path>`. On Windows this also happens when the file is
locked or quarantined.

### `NullPointerException` while building, from an env-var/system-property provider

The variable or property is unset: `System.getenv(...)` returns `null`. Set
`EMBEDDED_REDIS_EXECUTABLE` or `-Dembedded.redis.executable=...` in the environment that runs the
tests, or use a provider with a sensible fallback:

```java
ExecutableProvider provider = () -> {
    String path = System.getenv("EMBEDDED_REDIS_EXECUTABLE");
    return path != null ? new File(path) : newJarResourceProvider().get();
};
```

### `OsArchitectureNotFound: No Redis executable is provided for <os> <arch>`

No bundled binary for that platform (32-bit Windows, musl/Alpine, RISC-V, …). Supply your own —
see [Executables and binaries](executables.md).

### `Unrecognized OS: …`

`os.name` is not Windows, a Unix variant, or macOS. Use a custom `ExecutableProvider`; detection is
only used by the default jar-resource provider.

### `libssl.so.3: cannot open shared object file` / `'…/libssl.3.dylib' (no such file)`

The bundled binary has TLS compiled in but the host lacks the OpenSSL library. Install it
(`brew install openssl@3`, `sudo apt-get install openssl` — inside the image, not the host), or use a
binary built without TLS such as `REDIS_7_2_MACOSX_14_SONOMA_HANKCP`. Full details:
[Executables and binaries](executables.md#tls-and-openssl-troubleshooting).

### `Address already in use` (in the captured stderr)

Something else already holds the port: a previous test run that did not stop, another suite, or the
fixed sequence ports (6379/26379) used by two parallel runs. Use ephemeral ports —
`cluster.ephemeral()`, or `new ServerSocket(0)` for a single server.

### `Ran out of Redis ports`

A predefined port list was shorter than the number of nodes the cluster needs. Pass one port per
node, or switch to `ephemeral()`.

### Cluster start fails with `RuntimeException` caused by `RedisClusterSetupException`

The processes were up but the topology did not come together before `initializationTimeout`
(default 20s for a sharded cluster). The cause names the step: a node never appeared in
`cluster nodes`, `cluster_state` never reached `ok`, or a specific port failed to be added.

- Slow machines: raise it — `RedisShardedCluster.newRedisCluster().initializationTimeout(Duration.ofSeconds(60))`
- Check the node logs with `serrListener` for the real error (bad config, missing library, port clash)
- Sharded clusters need ports below 50000 for the cluster bus; `ephemeralServers()` already enforces
  this, hand-picked ports might not

Whatever happened, `start()` cleans up after itself: running nodes are stopped before the exception
propagates, so nothing is left holding a port.

### Sentinel keeps failing over / client cannot find the master

Three common reasons:

1. **Discovery is asynchronous.** Wait for `SENTINEL masters` to report the master before creating
   your `JedisSentinelPool`; a fixed `Thread.sleep` is a race.
2. **Bind address mismatch.** Sentinel announces its bind address; Jedis resolves `localhost` via
   `Inet4Address.getLocalHost()`, which is `127.0.1.1` on Debian/Ubuntu. Bind explicitly with
   `sentinelBuilder.bind(Inet4Address.getLocalHost().getHostAddress())`.
3. **Password mismatch.** If the master or sentinel has `requirepass`, configure the same password on
   the client and on every node through `withSentinelBuilder(...)`/`withServerBuilder(...)`.

### Downloading a binary fails or takes a minute

`newCachedUrlProvider` uses 60 second connect/read timeouts and fails with an `IOException` naming
the URI and HTTP status. In restricted environments, pre-seed the cache file (it is used as-is when
present) or configure an `ExecutableProvider` that needs no network at all.

### A Windows firewall popup appears on every run

The generated config starts with `bind 127.0.0.1`, so the popup means the effective config binds
something else — an inherited `configFile(...)`/`setting("bind ...")`, or a `bind 0.0.0.0` line
coming after the default. Check the generated temp file (see the checklist below) and either remove
the offending line or allow the binary once through the firewall.

### The binary disappears or will not run on Windows antivirus

The bundled Windows binaries are checked regularly (results in
[`virusscans/`](virusscans/)), but aggressive scanners may quarantine a freshly extracted file in
`%TEMP%`. Allow-list the temp directory or provide a binary from a location your scanner trusts.

## Diagnostics checklist

1. Reproduce with listeners attached — `serrListener` usually tells the story.
2. Check `isActive()` before/after `start()`; `false` after a successful `start()` means the process
   died right after printing its ready-line.
3. Print the generated config: it is a temp file named `embedded-redis-server_<port>*.conf`;
   `buildCommandArgs()` gives you the same command line the process runs with.
4. Confirm the binary yourself: run the path from `ExecutableProvider.get()` by hand —
   `./redis-server /path/to.conf --port 6380`.
5. Confirm the port: `ss -ltnp | grep 6379` (or `netstat -ano | findstr 6379` on Windows).
6. Rule out platform detection: `OsArchitecture` values are logged by
   `OsArchitectureNotFound`, and `uname -m` / `PROCESSOR_ARCHITECTURE` decide the architecture.
