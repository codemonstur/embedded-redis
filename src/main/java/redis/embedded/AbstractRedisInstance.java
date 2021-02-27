package redis.embedded;

import redis.embedded.exceptions.EmbeddedRedisException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static redis.embedded.util.IOUtils.*;

abstract class AbstractRedisInstance implements Redis {

    public final Pattern readyPattern;
    public final int port;

    protected List<String> args = Collections.emptyList();

    private volatile boolean active = false;
    private Process redisProcess;

    protected AbstractRedisInstance(final int port, final String readyPattern) {
        this.port = port;
        this.readyPattern = Pattern.compile(readyPattern);
    }

    public synchronized void start() throws EmbeddedRedisException {
        if (active) return;

        try {
            redisProcess = new ProcessBuilder(args)
                .directory(new File(args.get(0)).getParentFile())
                .start();
            addShutdownHook("RedisInstanceCleaner", this::stop);
            logStream(redisProcess.getErrorStream(), System.out::println);
            awaitServerReady();

            active = true;
        } catch (IOException e) {
            throw new EmbeddedRedisException("Failed to start Redis instance", e);
        }
    }

    private void awaitServerReady() throws IOException {
        final StringBuilder log = new StringBuilder();
        if (!findMatchInStream(redisProcess.getInputStream(), readyPattern, log))
            throw new EmbeddedRedisException("Can't start redis server. Check logs for details. Redis process log: " + log.toString());
    }

    public synchronized void stop() throws EmbeddedRedisException {
        if (!active) return;

        try {
            redisProcess.destroy();
            redisProcess.waitFor();
            active = false;
        } catch (InterruptedException e) {
            throw new EmbeddedRedisException("Failed to stop redis instance", e);
        }
    }

    public boolean isActive() {
        return active;
    }

    public List<Integer> ports() {
        return Arrays.asList(port);
    }

}
