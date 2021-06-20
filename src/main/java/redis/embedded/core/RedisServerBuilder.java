package redis.embedded.core;

import redis.embedded.RedisServer;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static redis.embedded.Redis.DEFAULT_REDIS_PORT;
import static redis.embedded.core.ExecutableProvider.newEmbeddedRedis2_8_19Provider;

public final class RedisServerBuilder {

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private File executable;
    private ExecutableProvider provider = newEmbeddedRedis2_8_19Provider();
    private String bindAddress = "127.0.0.1";
    private int bindPort = DEFAULT_REDIS_PORT;
    private InetSocketAddress slaveOf;

    private StringBuilder redisConfigBuilder = new StringBuilder();

    public RedisServerBuilder executableProvider(final ExecutableProvider provider) {
        this.provider = provider;
        return this;
    }

    public RedisServerBuilder bind(final String bind) {
        this.bindAddress = bind;
        return this;
    }

    public RedisServerBuilder port(final int port) {
        this.bindPort = port;
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

    public RedisServerBuilder configFile(final String redisConf) throws IOException {
        return configFile(Paths.get(redisConf));
    }
    public RedisServerBuilder configFile(final Path redisConf) throws IOException {
        Files.lines(redisConf).forEach(line -> redisConfigBuilder.append(line).append(LINE_SEPARATOR));
        return this;
    }

    public RedisServerBuilder settingIf(final boolean shouldSet, final String configLine) {
        if (shouldSet) setting(configLine);
        return this;
    }

    public RedisServerBuilder setting(final String configLine) {
        redisConfigBuilder.append(configLine).append(LINE_SEPARATOR);
        return this;
    }

    public RedisServer build() throws IOException {
        return new RedisServer(bindPort, buildCommandArgs());
    }

    public void reset() {
        this.executable = null;
        this.slaveOf = null;
        this.redisConfigBuilder = new StringBuilder();
        this.provider = newEmbeddedRedis2_8_19Provider();
        this.bindAddress = "127.0.0.1";
        this.bindPort = DEFAULT_REDIS_PORT;
    }

    public List<String> buildCommandArgs() throws IOException {
        setting("bind " + bindAddress);

        final Path redisConfigFile =
            writeNewRedisConfigFile("embedded-redis-server_"+bindPort, redisConfigBuilder.toString());

        executable = provider.get();

        final List<String> args = new ArrayList<>();
        args.add(executable.getAbsolutePath());
        args.add(redisConfigFile.toAbsolutePath().toString());
        args.add("--port");
        args.add(Integer.toString(bindPort));

        if (slaveOf != null) {
            args.add("--slaveof");
            args.add(slaveOf.getHostName());
            args.add(Integer.toString(slaveOf.getPort()));
        }

        return args;
    }

    private static Path writeNewRedisConfigFile(final String prefix, final String contents) throws IOException {
        final Path redisConfigFile = Files.createTempFile(prefix, ".conf");
        redisConfigFile.toFile().deleteOnExit();
        Files.write(redisConfigFile, contents.getBytes(UTF_8));
        return redisConfigFile;
    }

}
