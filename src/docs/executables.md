# Executables and binaries

embedded-redis does not implement Redis — it launches the real `redis-server`. Which binary is used
is decided by an [`ExecutableProvider`](../main/java/redis/embedded/core/ExecutableProvider.java), a
one-method interface:

```java
public interface ExecutableProvider {
    File get() throws IOException;
}
```

Every builder has an `executableProvider(...)` option; the default is `newJarResourceProvider()`.
`get()` is called when the instance is **built** (not when it is started), and it must return the
path of an existing file. `start()` additionally verifies that the file is executable.

## The default: binary from the jar

`newJarResourceProvider()` detects the current OS and architecture and copies the matching resource
out of the jar into a fresh temporary directory (marked `deleteOnExit()`), then makes it executable.

Detection uses `os.name` for the platform (`WINDOWS`, `UNIX`, `MAC_OS_X`) and `uname -m` on Unix/macOS
or `PROCESSOR_ARCHITECTURE`/`PROCESSOR_ARCHITEW6432` on Windows for the architecture.

### Bundled binaries

| OS | Architecture | Resource in the jar | Redis version |
|---|---|---|---|
| Linux/Unix | x86 | `redis-server-6.2.7-linux-386` | 6.2.7 |
| Linux/Unix | x86_64 | `redis-server-6.2.6-v5-linux-amd64` | 6.2.11 (redis-stack 6.2.6-v5) |
| Linux/Unix | aarch64 | `redis-server-6.2.7-linux-arm64` | 6.2.7 |
| macOS | x86_64 | `redis-server-6.2.6-v5-darwin-amd64` | 6.2.11 (redis-stack 6.2.6-v5) |
| macOS | arm64 | `redis-server-6.2.6-v5-darwin-arm64` | 6.2.11 (redis-stack 6.2.6-v5) |
| Windows | x86_64 | `redis-server-5.0.14.1-windows-amd64.exe` | 5.0.14.1 |

A platform without an entry — for example 32-bit Windows — fails with `OsArchitectureNotFound`
when the instance is built.

VirusTotal scan results for these binaries live in [`virusscans/`](virusscans/).

### Overriding the resource map

You can swap in different resources that are on your classpath:

```java
Map<OsArchitecture, String> executables = new HashMap<>();
executables.put(UNIX_x86_64, "/my-redis-7.2.4-linux-amd64");
// ... every platform you need to support ...

RedisServer redis = RedisServer.newRedisServer()
    .executableProvider(newJarResourceProvider(executables))
    .build();
```

Available factory variants:

| Factory | Behaviour |
|---|---|
| `newJarResourceProvider()` | Default map, new temp directory per `get()` |
| `newJarResourceProvider(File tempDir)` | Default map, all binaries extracted into one directory |
| `newJarResourceProvider(IOSupplier<File>)` | Default map, directory supplied lazily (for example per call) |
| `newJarResourceProvider(Map<OsArchitecture, String>)` | Custom map, new temp directory per `get()` |
| `newJarResourceProvider(IOSupplier<File>, Map)` | Custom map, custom directory |
| `newFileThenJarResourceProvider(Map)` | Use `new File(path)` when that file exists, otherwise fall back to the jar resource `path` |

Because the default extracts into a *new* temp directory on every call, each node of a cluster gets
its own working directory — which is what keeps per-node files such as `nodes.conf` apart.

## Choosing a different binary

### From the file system

```java
RedisServer redis = new RedisServer(6379, new File("/path/to/redis-server"));
```

or with the builder:

```java
RedisServer.newRedisServer().executableProvider(() -> new File("/path/to/redis-server")).build();
```

An `ExecutableProvider` is a functional interface, so a lambda is a perfectly good implementation.

### From an environment variable or system property

```java
RedisServer.newRedisServer()
    .executableProvider(newEnvironmentVariableProvider())   // reads EMBEDDED_REDIS_EXECUTABLE
    .build();

RedisServer.newRedisServer()
    .executableProvider(newSystemPropertyProvider())        // reads -Dembedded.redis.executable=/path/to/redis
    .build();
```

Custom names are supported: `newEnvironmentVariableProvider("MY_REDIS")`,
`newSystemPropertyProvider("my.redis")`. The variable or property **must be set** — reading an unset
one fails with a `NullPointerException` when the instance is built, so wire this up through your CI
configuration rather than as an optional extra.

### From `PATH`

```java
RedisServer.newRedisServer()
    .executableProvider(newExecutableInPath("redis-server"))
    .build();
```

`PATH` is searched when the factory is called; a missing binary throws `FileNotFoundException` right
there rather than later at `start()`.

### By download (cached URL)

```java
import static redis.embedded.core.ExecutableProvider.newCachedUrlProvider;
import static redis.embedded.core.ExecutableProvider.REDIS_7_2_MACOSX_14_SONOMA_HANKCP;

Path cache = Paths.get(System.getProperty("java.io.tmpdir"), "redis-binary");
RedisServer redis = RedisServer.newRedisServer()
    .executableProvider(newCachedUrlProvider(cache, REDIS_7_2_MACOSX_14_SONOMA_HANKCP))
    .build();
```

Semantics of `newCachedUrlProvider(Path cachedLocation, URI uri)`:

- If `cachedLocation` already exists it is used as-is — no network access at all. Point it at a
  file pre-seeded in a Docker image or CI cache to make builds reproducible offline.
- Otherwise the URI is downloaded with a 60 second connect and read timeout, written to a
  `.part` file next to the target, made executable, and moved into place atomically, so an
  interrupted download never leaves a half-written binary in the cache.
- A non-200 response raises an `IOException` that names the URI and the status code.

### Published extra binaries

Binaries that are not bundled in the jar are stored in
[`src/main/binaries`](../main/binaries) in this repository (4 files) and referenced by URI
constants on `ExecutableProvider`:

| Constant | Binary |
|---|---|
| `REDIS_7_2_5_WINDOWS_ZKTECO` | Redis 7.2.5 for Windows |
| `REDIS_8_8_0_WINDOWS_ZKTECO` | Redis 8.8.0 for Windows (zkteco-home/redis-windows) |
| `REDIS_8_10_1_WINDOWS_ZKTECO` | Redis 8.10.1 for Windows (zkteco-home/redis-windows) |
| `REDIS_7_2_MACOSX_14_SONOMA_HANKCP` | Redis 7.2 for macOS arm64, **no TLS support** |
| `REDIS_7_2_LINUX_JAMMY_X86_64` | redis-stack-server 7.2.0-v6 for Linux x86_64 |
| `REDIS_7_2_LINUX_JAMMY_ARM_64` | redis-stack-server 7.2.0-v6 for Linux arm64 |

Example using one of these: [`src/test/java/tools/DownloadUriTest.java`](../test/java/tools/DownloadUriTest.java).

## TLS and OpenSSL troubleshooting

The bundled binaries have TLS support compiled in, which means they link against a TLS library on
the host. When it is missing, the process dies before printing its ready-line and `start()` fails.

macOS:

```
'/opt/homebrew/opt/openssl@3/lib/libssl.3.dylib' (no such file),
'/System/Volumes/Preboot/Cryptexes/OS/opt/homebrew/opt/openssl@3/lib/libssl.3.dylib' (no such file),
'/usr/lib/libssl.3.dylib' (no such file, not in dyld cache)
```

Linux:

```
error while loading shared libraries: libssl.so.3: cannot open shared object file: No such file or directory
```

Fixes, in order of preference:

1. Install OpenSSL on the host: `brew install openssl@3` on macOS, `sudo apt-get install openssl`
   on Debian/Ubuntu. Inside a Docker image the package must be installed **in the image**.
2. Use a binary built without TLS, for example `REDIS_7_2_MACOSX_14_SONOMA_HANKCP`, or one you
   compiled yourself.

## Compiling your own binary

If your platform has no bundled binary — or you need a specific Redis version — build Redis
yourself and point an `ExecutableProvider` at the result.

- Any Redis source release works: <https://github.com/redis/redis/releases>
- For arm64 there is a prepared target: `make build-arm`, which builds a Docker image from
  [`compile/DockerArm`](compile/DockerArm) and compiles Redis with
  [`compile/compile-redis.sh`](compile/compile-redis.sh). The finished binary lands in
  `target/redis-<version>/src/redis-server`.

  > Build on real ARM hardware if you can: a QEMU-generated binary behaves differently enough that
  > Redis' own test suite fails with it (see PR [#2](https://github.com/codemonstur/embedded-redis/pull/2)).

To ship such a binary with the library, add it under `src/main/resources` (bundled) or
`src/main/binaries` (downloaded) and register it in `newProvidedVersionsMap()` or as a URI constant.
