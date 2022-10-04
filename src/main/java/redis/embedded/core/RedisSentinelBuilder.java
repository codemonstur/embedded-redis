package redis.embedded.core;

import redis.embedded.RedisSentinel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static redis.embedded.Redis.DEFAULT_REDIS_PORT;
import static redis.embedded.core.ExecutableProvider.newEmbeddedRedis2_8_19Provider;

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
    private ExecutableProvider executableProvider = newEmbeddedRedis2_8_19Provider();
    private String bind = "127.0.0.1";
    private Integer port = 26379;
    private int masterPort = DEFAULT_REDIS_PORT;
    private String masterName = "mymaster";
    private long downAfterMilliseconds = 60000L;
    private long failOverTimeout = 180000L;
    private int parallelSyncs = 1;
    private int quorumSize = 1;
    private String sentinelConf;
    private boolean forceStop = false;

    private StringBuilder redisConfigBuilder;

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

    public RedisSentinelBuilder quorumSize(int quorumSize) {
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
        if (sentinelConf != null) {
            throw new IllegalArgumentException("Redis configuration is already set using redis conf file");
        }

        if (redisConfigBuilder == null) {
            redisConfigBuilder = new StringBuilder();
        }

        redisConfigBuilder.append(configLine).append(LINE_SEPARATOR);
        return this;
    }

    public RedisSentinelBuilder onShutdownForceStop(final boolean forceStop) {
        this.forceStop = forceStop;
        return this;
    }

    public RedisSentinel build() {
        tryResolveConfAndExec();
        return new RedisSentinel(port, buildCommandArgs(), forceStop);
    }

    private void tryResolveConfAndExec() {
        try {
            if (sentinelConf == null) {
                resolveSentinelConf();
            }
            executable = executableProvider.get();
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not build sentinel instance", e);
        }
    }

    public void reset() {
        this.redisConfigBuilder = null;
        this.sentinelConf = null;
    }

    public void addDefaultReplicationGroup() {
        setting(String.format(LINE_MASTER_MONITOR, masterName, masterPort, quorumSize));
        setting(String.format(LINE_DOWN_AFTER, masterName, downAfterMilliseconds));
        setting(String.format(LINE_FAIL_OVER, masterName, failOverTimeout));
        setting(String.format(LINE_PARALLEL_SYNCS, masterName, parallelSyncs));
    }

    private void resolveSentinelConf() throws IOException {
        if (redisConfigBuilder == null) {
            addDefaultReplicationGroup();
        }
        setting("bind " + bind);
        setting(String.format(LINE_PORT, port));
        final String configString = redisConfigBuilder.toString();

        File redisConfigFile = File.createTempFile(resolveConfigName(), ".conf");
        redisConfigFile.deleteOnExit();
        Files.write(redisConfigFile.toPath(), configString.getBytes(UTF_8));
        sentinelConf = redisConfigFile.getAbsolutePath();
    }

    private String resolveConfigName() {
        return CONF_FILENAME + "_" + port;
    }

    private List<String> buildCommandArgs() {
        List<String> args = new ArrayList<>();
        args.add(executable.getAbsolutePath());
        args.add(sentinelConf);
        args.add("--sentinel");

        if (port != null) {
            args.add("--port");
            args.add(Integer.toString(port));
        }

        return args;
    }
}
