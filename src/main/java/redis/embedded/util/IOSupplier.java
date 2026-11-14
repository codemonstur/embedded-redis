package redis.embedded.util;

import java.io.IOException;

/**
 * The {@code IOSupplier} interface.
 *
 * @param <T> the type parameter
 */
public interface IOSupplier<T> {

    /**
     * Returns the result of {@code get}.
     *
     * @return the result
     * @throws IOException if the operation fails
     */
    T get() throws IOException;

}
