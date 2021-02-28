package redis.embedded;

import java.io.IOException;
import java.util.List;

public interface Redis {
    boolean isActive();

    void start() throws IOException;

    void stop() throws IOException;

    List<Integer> ports();
}
