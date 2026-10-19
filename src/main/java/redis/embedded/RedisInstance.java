package redis.embedded;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static redis.embedded.util.IO.*;

public abstract class RedisInstance implements Redis {

    // A binary that never signals readiness (missing libraries, wrong architecture, ...) would otherwise block
    // the caller forever. Override with -Dembedded-redis.readyTimeoutSeconds=N
    private static final int READY_TIMEOUT_SECONDS = Integer.getInteger("embedded-redis.readyTimeoutSeconds", 60);
    // A wedged process would otherwise hang the shutdown of the test-suite forever.
    // Override with -Dembedded-redis.stopTimeoutSeconds=N
    private static final int STOP_TIMEOUT_SECONDS = Integer.getInteger("embedded-redis.stopTimeoutSeconds", 15);

    private final Pattern readyPattern;
    private final int port;
    private final List<String> args;
    private final boolean forceStop;
    private final Consumer<String> soutListener;
    private final Consumer<String> serrListener;

    private volatile boolean active = false;
    private Process process;
    private Thread shutdownHook;

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
        final File executable = new File(args.get(0));

        if (!executable.isFile())
            throw new FileNotFoundException("Redis binary " + args.get(0) + " could not be found");
        if (!executable.canExecute())
            throw new AccessDeniedException("Redis binary " + args.get(0) + " is not executable");

        try {
            final Process startedProcess = new ProcessBuilder(args)
                .directory(executable.getParentFile())
                .start();
            process = startedProcess;
            shutdownHook = new Thread(checkedToRuntime(this::stop), "RedisInstanceCleaner");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            awaitServerReady(startedProcess, readyPattern, soutListener, serrListener);

            // Both streams are drained unconditionally: redis logs to stdout by default and blocks once the
            // pipe fills up, so even without listeners the output has to be read (and then simply discarded)
            newDaemonThread(() -> logStream(startedProcess.getErrorStream(), serrListener)).start();
            newDaemonThread(() -> logStream(startedProcess.getInputStream(), soutListener)).start();

            active = true;
        } catch (final IOException e) {
            removeShutdownHook();
            throw new IOException("Failed to start Redis service", e);
        }
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
        final boolean ready = findMatchInStream(process.getInputStream(), readyPattern, soutListener, log
                , TimeUnit.SECONDS.toMillis(READY_TIMEOUT_SECONDS));
        if (ready) return;

        // The process never signaled readiness. Stop it before reading the error-stream: reading the stderr of a
        // process that is still running would block until it exits, turning a startup failure into a hung build.
        final boolean timedOut = process.isAlive();
        process.destroyForcibly();
        try {
            process.waitFor(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        final String stdOut = log.toString();
        final String stdErr = readFully(process.getErrorStream(), serrListener);

        throw new IOException("Redis-server process appears not to have started. "
            + (timedOut ? "It did not print '" + readyPattern + "' within " + READY_TIMEOUT_SECONDS + " seconds. " : "")
            + (isNullOrEmpty(stdOut) ? "No output was found in standard-out." : "standard-out contains this: " + stdOut)
            + " "
            + (isNullOrEmpty(stdErr) ? "No output was found in standard-err." : "standard-err contains this: " + stdErr)
        );
    }
    private static boolean isNullOrEmpty(final String value) {
        return value == null || value.isEmpty();
    }

    public synchronized void stop() throws IOException {
        removeShutdownHook();
        if (process == null) return;

        try {
            if (forceStop) {
                process.destroyForcibly();
                // Waiting for the process to actually die frees its port before stop() returns
                process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } else {
                process.destroy();
                // A process that ignores the destroy signal would otherwise hang the build forever
                if (!process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            }
        } catch (final InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Failed to stop redis service", e);
        } finally {
            active = false;
        }
    }

    // Unregistered once stopped: a stale hook would pin this instance in memory until the JVM exits, and
    // instances that are started repeatedly would pile up one hook per start
    private void removeShutdownHook() {
        if (shutdownHook == null) return;
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (final IllegalStateException ignored) {
            // The JVM is already shutting down and running shutdown-hooks cannot be removed
        }
        shutdownHook = null;
    }

    public boolean isActive() {
        return active;
    }

    public List<Integer> ports() {
        return Collections.singletonList(port);
    }

}
