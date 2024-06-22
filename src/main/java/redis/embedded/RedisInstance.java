package redis.embedded;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.embedded.model.RedisConfig;
import redis.embedded.util.IO;
import redis.embedded.util.RedisConfigParser;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static redis.embedded.util.IO.*;

public abstract class RedisInstance implements Redis {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisInstance.class);

    private final Pattern readyPattern;
    private final int port;
    private final List<String> args;
    private final boolean forceStop;
    private final Consumer<String> soutListener;
    private final Consumer<String> serrListener;

    private volatile boolean active = false;
    private Process process;

    protected RedisInstance(final int port, final List<String> args, final Pattern readyPattern,
                            final boolean forceStop, final Consumer<String> soutListener,
                            final Consumer<String> serrListener) {
        this.port = port;
        this.args = args;
        this.readyPattern = readyPattern;
        this.forceStop = forceStop;
        this.soutListener = soutListener;
        this.serrListener = serrListener;
    }

    public synchronized void start() throws IOException {
        if (active) return;

        try {
            process = new ProcessBuilder(args)
                .directory(getRedisDir().toFile())
                .start();
            addShutdownHook("RedisInstanceCleaner", checkedToRuntime(this::stop));
            awaitServerReady(process, readyPattern, soutListener, serrListener);

            if (serrListener != null)
                newDaemonThread(() -> logStream(process.getErrorStream(), serrListener)).start();
            if (soutListener != null)
                newDaemonThread(() -> logStream(process.getInputStream(), soutListener)).start();

            active = true;
        } catch (final IOException e) {
            throw new IOException("Failed to start Redis service", e);
        }
    }

    private Path getRedisDir() {
        return Paths.get(args.get(0)).getParent();
    }

    // You might get an error when you try to start the default binary without having openssl installed. The default
    // binaries have TLS support but require a library on the host OS. On MacOS you will probably get an error that
    // looks like this:
    //
    //     '/opt/homebrew/opt/openssl@3/lib/libssl.3.dylib' (no such file),
    //     '/System/Volumes/Preboot/Cryptexes/OS/opt/homebrew/opt/openssl@3/lib/libssl.3.dylib' (no such file),
    //     '/opt/homebrew/opt/openssl@3/lib/libssl.3.dylib' (no such file),
    //     '/usr/lib/libssl.3.dylib' (no such file, not in dyld cache)
    //
    // One option for resolving the issue is to install openssl using `brew install openssl@3`. Alternatively, you
    // can use a binary that doesn't have TLS support. Either by compiling your own from source, or by using HankCP's
    // binary at ExecutableProvider.REDIS_7_2_MACOSX_14_SONOMA_HANKCP, or downloading one from some other place.
    //
    // On linux the error will look like this:
    //
    //     /app/redis-server-6.2.6-v5-linux-amd64: error while loading shared libraries: libssl.so.3: cannot open
    //     shared object file: No such file or directory
    //
    // The problem is the same as on MacOS. You need a binary that doesn't require the libssl library or you need to
    // provide that library. If you are running the app on your host you can install the needed package using your
    // package manager. Such as with apt-get (sudo apt-get install libssl1.0.0 libssl-dev). If you are running this
    // inside a docker image you'll need to make sure the library is available inside the image.

    private static void awaitServerReady(final Process process, final Pattern readyPattern
            , final Consumer<String> soutListener, final Consumer<String> serrListener) throws IOException {
        final StringBuilder log = new StringBuilder();
        if (!findMatchInStream(process.getInputStream(), readyPattern, soutListener, log)) {
            final String stdOut = log.toString();
            final String stdErr = readFully(process.getErrorStream(), serrListener);

            throw new IOException("Redis-server process appears not to have started. "
                + (isNullOrEmpty(stdOut) ? "No output was found in standard-out." : "stdandard-out contains this: " + stdOut)
                + " "
                + (isNullOrEmpty(stdErr) ? "No output was found in standard-err." : "stdandard-err contains this: " + stdErr)
            );
        }
    }
    private static boolean isNullOrEmpty(final String value) {
        return value == null || value.isEmpty();
    }

    public synchronized void stop() throws IOException {
        if (!active) return;

        try {
            if (forceStop)
                process.destroyForcibly();
            else {
                process.destroy();
                process.waitFor();
            }
            active = false;
        } catch (final InterruptedException e) {
            throw new IOException("Failed to stop redis service", e);
        } finally {
            findAndDeleteClusterConfigFiles();
        }
    }

    private void findAndDeleteClusterConfigFiles() {
        findRedisConfigFile().ifPresent(this::findAndDeleteClusterConfigFiles);
    }

    private Optional<Path> findRedisConfigFile() {
        return args.stream().filter(arg -> arg.endsWith(".conf")).findFirst().map(Paths::get);
    }

    private void findAndDeleteClusterConfigFiles(final Path redisConfig) {
        try {
            final RedisConfig config = new RedisConfigParser().parse(redisConfig);
            config.directives("cluster-config-file")
                    .stream()
                    .map(RedisConfig.Directive::arguments)
                    .flatMap(List::stream)
                    .map(clusterConfFile -> getRedisDir().resolve(clusterConfFile))
                    .forEach(IO::deleteSafely);
        } catch (IOException e) {
            LOGGER.error("Unable to parse redis config file", e);
        }

    }

    public boolean isActive() {
        return active;
    }

    public List<Integer> ports() {
        return Collections.singletonList(port);
    }

}
