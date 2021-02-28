package redis.embedded;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

public interface Redis {
    int DEFAULT_REDIS_PORT = 6379;
    Pattern SERVER_READY_PATTERN = Pattern.compile(".*[Rr]eady to accept connections.*");
    Pattern SENTINEL_READY_PATTERN = Pattern.compile(".*Sentinel (runid|ID) is.*");

    boolean isActive();

    void start() throws IOException;

    void stop() throws IOException;

    List<Integer> ports();
}
