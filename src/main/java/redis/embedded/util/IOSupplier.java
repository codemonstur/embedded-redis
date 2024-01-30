package redis.embedded.util;

import java.io.IOException;

public interface IOSupplier<T> {

    T get() throws IOException;

}
