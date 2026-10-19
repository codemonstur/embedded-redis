package redis.embedded.core;

import redis.embedded.RedisSentinel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static redis.embedded.Redis.DEFAULT_REDIS_PORT;
import static redis.embedded.core.ExecutableProvider.newJarResourceProvider;

public final class RedisSentinelBuilder {

    private static final String
        LINE_SEPARATOR = System.getProperty("line.separator"),
        CONF_FILENAME = "embedded-redis-sentinel",
        LINE_MASTER_MONITOR = "sentinel monitor %s 127.0.0.1 %d %d",
        LINE_DOWN_AFTER = "sentinel down-after-milliseconds %s %d",
        LINE_FAIL_OVER = "sentinel failover-timeout %s %d",
        LINE_PARALLEL_SYNCS = "sentinel parallel-syncs %s %d",
        LINE_PORT = "port %d";

    private File executable;

    private ExecutableProvider executableProvider = newJarResourceProvider();
    private String bind = "127.0.0.1";
    private Integer port = 26379;
    private int masterPort = DEFAULT_REDIS_PORT;
    private String masterName = "mymaster";
    private long downAfterMilliseconds = 60000L;
    private long failOverTimeout = 180000L;
    private int parallelSyncs = 1;
    private int quorumSize = 1;
    // User-provided configuration; never mutated by build(), so a builder can safely be shared between
    // the sentinels of a cluster
    private String sentinelConf;
    private StringBuilder redisConfigBuilder;
    private final StringBuilder replicationGroups = new StringBuilder();

    private boolean forceStop = false;
    private Consumer<String> soutListener;
    private Consumer<String> serrListener;

    public RedisSentinelBuilder executableProvider(final ExecutableProvider executableProvider) {
        this.executableProvider = executableProvider;
        return this;
    }

    public RedisSentinelBuilder bind(final String bind) {
        this.bind = bind;
        return this;
    }

    public RedisSentinelBuilder port(final Integer port) {
        this.port = port;
        return this;
    }

    public RedisSentinelBuilder masterPort(final Integer masterPort) {
        this.masterPort = masterPort;
        return this;
    }

    public RedisSentinelBuilder masterName(final String masterName) {
        this.masterName = masterName;
        return this;
    }

    public RedisSentinelBuilder quorumSize(final int quorumSize) {
        this.quorumSize = quorumSize;
        return this;
    }

    public RedisSentinelBuilder downAfterMilliseconds(final long downAfterMilliseconds) {
        this.downAfterMilliseconds = downAfterMilliseconds;
        return this;
    }

    public RedisSentinelBuilder failOverTimeout(final long failoverTimeout) {
        this.failOverTimeout = failoverTimeout;
        return this;
    }

    public RedisSentinelBuilder parallelSyncs(final int parallelSyncs) {
        this.parallelSyncs = parallelSyncs;
        return this;
    }

    public RedisSentinelBuilder configFile(final String redisConf) {
        if (redisConfigBuilder != null) {
            throw new IllegalArgumentException("Redis configuration is already partially built using setting(String) method");
        }
        this.sentinelConf = redisConf;
        return this;
    }

    public RedisSentinelBuilder settingIf(final boolean shouldSet, final String configLine) {
        if (shouldSet) setting(configLine);
        return this;
    }

    public RedisSentinelBuilder setting(final String configLine) {
        if (sentinelConf != null) throw new IllegalArgumentException("Redis configuration is already set using redis conf file");
        if (redisConfigBuilder == null) redisConfigBuilder = new StringBuilder();

        redisConfigBuilder.append(configLine).append(LINE_SEPARATOR);
        return this;
    }

    public RedisSentinelBuilder onShutdownForceStop(final boolean forceStop) {
        this.forceStop = forceStop;
        return this;
    }

    public RedisSentinelBuilder soutListener(final Consumer<String> soutListener) {
        this.soutListener = soutListener;
        return this;
    }
    public RedisSentinelBuilder serrListener(final Consumer<String> serrListener) {
        this.serrListener = serrListener;
        return this;
    }

    public RedisSentinel build() {
        try {
            final String conf = resolveConfPath();
            executable = executableProvider.get();
            return new RedisSentinel(port, buildCommandArgs(conf), forceStop, soutListener, serrListener);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Could not build sentinel instance", e);
        }
    }

    // Drops the auto-generated replication-group lines: the next build starts from the user configuration again
    public void reset() {
        this.replicationGroups.setLength(0);
    }

    public void addDefaultReplicationGroup() {
        appendDefaultReplicationGroup(replicationGroups);
    }

    // Composes the config file freshly on every build; the user-provided settings are appended unmodified
    // so that a builder can be reused (or shared between the sentinels of a cluster) without accumulating
    private String resolveConfPath() throws IOException {
        if (sentinelConf != null) return sentinelConf;

        final StringBuilder config = new StringBuilder();
        if (redisConfigBuilder != null) config.append(redisConfigBuilder);
        if (replicationGroups.length() > 0) config.append(replicationGroups);
        else appendDefaultReplicationGroup(config);
        config.append("bind ").append(bind).append(LINE_SEPARATOR);
        config.append(String.format(LINE_PORT, port)).append(LINE_SEPARATOR);

        final File redisConfigFile = File.createTempFile(resolveConfigName(), ".conf");
        redisConfigFile.deleteOnExit();
        Files.write(redisConfigFile.toPath(), config.toString().getBytes(UTF_8));
        return redisConfigFile.getAbsolutePath();
    }

    private void appendDefaultReplicationGroup(final StringBuilder config) {
        config.append(String.format(LINE_MASTER_MONITOR, masterName, masterPort, quorumSize)).append(LINE_SEPARATOR);
        config.append(String.format(LINE_DOWN_AFTER, masterName, downAfterMilliseconds)).append(LINE_SEPARATOR);
        config.append(String.format(LINE_FAIL_OVER, masterName, failOverTimeout)).append(LINE_SEPARATOR);
        config.append(String.format(LINE_PARALLEL_SYNCS, masterName, parallelSyncs)).append(LINE_SEPARATOR);
    }

    private String resolveConfigName() {
        return CONF_FILENAME + "_" + port;
    }

    private List<String> buildCommandArgs(final String conf) {
        final List<String> args = new ArrayList<>();
        args.add(executable.getAbsolutePath());
        args.add(conf);
        args.add("--sentinel");

        if (port != null) {
            args.add("--port");
            args.add(Integer.toString(port));
        }

        return args;
    }

}
