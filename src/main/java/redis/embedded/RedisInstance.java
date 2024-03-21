package redis.embedded;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static redis.embedded.util.IO.*;

public abstract class RedisInstance implements Redis {

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
                .directory(new File(args.get(0)).getParentFile())
                .start();
            addShutdownHook("RedisInstanceCleaner", checkedToRuntime(this::stop));
            if (serrListener != null)
                newDaemonThread(() -> logStream(process.getErrorStream(), serrListener)).start();
            if (soutListener != null)
                newDaemonThread(() -> logStream(process.getInputStream(), soutListener)).start();

            awaitServerReady(process, readyPattern, soutListener);

            active = true;
        } catch (final IOException e) {
            throw new IOException("Failed to start Redis service", e);
        }
    }

    private static void awaitServerReady(final Process process, final Pattern readyPattern,
                                           final Consumer<String> soutListener) throws IOException {
        final StringBuilder log = new StringBuilder();
        if (!findMatchInStream(process.getInputStream(), readyPattern, soutListener, log))
            throw new IOException("Ready pattern not found in log. Startup log: " + log);
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
        }
    }

    public boolean isActive() {
        return active;
    }

    public List<Integer> ports() {
        return Collections.singletonList(port);
    }

}
