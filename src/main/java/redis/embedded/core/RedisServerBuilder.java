package redis.embedded.core;

import redis.embedded.RedisServer;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static redis.embedded.Redis.DEFAULT_REDIS_PORT;
import static redis.embedded.core.ExecutableProvider.newRedis2_8_19Provider;

public final class RedisServerBuilder {
    private static final String
        LINE_SEPARATOR = System.getProperty("line.separator"),
        CONF_FILENAME = "embedded-redis-server";

    private File executable;
    private ExecutableProvider executableProvider = newRedis2_8_19Provider();
    private String bind = "127.0.0.1";
    private int port = DEFAULT_REDIS_PORT;
    private InetSocketAddress slaveOf;
    private String redisConf;

    private StringBuilder redisConfigBuilder;

    public RedisServerBuilder redisExecProvider(ExecutableProvider executableProvider) {
        this.executableProvider = executableProvider;
        return this;
    }

    public RedisServerBuilder bind(final String bind) {
        this.bind = bind;
        return this;
    }

    public RedisServerBuilder port(final int port) {
        this.port = port;
        return this;
    }

    public RedisServerBuilder slaveOf(final String hostname, final int port) {
        this.slaveOf = new InetSocketAddress(hostname, port);
        return this;
    }

    public RedisServerBuilder slaveOf(final InetSocketAddress slaveOf) {
        this.slaveOf = slaveOf;
        return this;
    }

    public RedisServerBuilder configFile(final String redisConf) {
        if (redisConfigBuilder != null) {
            throw new IllegalArgumentException("Redis configuration is already partially built using setting(String) method");
        }
        this.redisConf = redisConf;
        return this;
    }

    public RedisServerBuilder setting(final String configLine) {
        if (redisConf != null) {
            throw new IllegalArgumentException("Redis configuration is already set using redis conf file");
        }

        if (redisConfigBuilder == null) {
            redisConfigBuilder = new StringBuilder();
        }

        redisConfigBuilder.append(configLine).append(LINE_SEPARATOR);
        return this;
    }

    public RedisServer build() {
        return new RedisServer(port, buildCommandArgs());
    }

    public void reset() {
        this.executable = null;
        this.redisConfigBuilder = null;
        this.slaveOf = null;
        this.redisConf = null;
    }

    public List<String> buildCommandArgs() {
        setting("bind " + bind);
        tryResolveConfAndExec();

        List<String> args = new ArrayList<>();
        args.add(executable.getAbsolutePath());

        if (redisConf != null && !redisConf.isEmpty()) {
            args.add(redisConf);
        }

        args.add("--port");
        args.add(Integer.toString(port));

        if (slaveOf != null) {
            args.add("--slaveof");
            args.add(slaveOf.getHostName());
            args.add(Integer.toString(slaveOf.getPort()));
        }

        return args;
    }

    private void tryResolveConfAndExec() {
        try {
            resolveConfAndExec();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not build server instance", e);
        }
    }

    private void resolveConfAndExec() throws IOException {
        if (redisConf == null && redisConfigBuilder != null) {
            File redisConfigFile = File.createTempFile(CONF_FILENAME + "_" + port, ".conf");
            redisConfigFile.deleteOnExit();
            Files.write(redisConfigFile.toPath(), redisConfigBuilder.toString().getBytes(UTF_8));
            redisConf = redisConfigFile.getAbsolutePath();
        }

        try {
            executable = executableProvider.get();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to resolve executable", e);
        }
    }

}
