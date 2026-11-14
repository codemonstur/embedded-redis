package redis.embedded.util;

/**
 * The {@code CheckedRunnable} interface.
 */
public interface CheckedRunnable {

    /**
     * Runs the operation.
     *
     * @throws Exception if the operation fails
     */
    void run() throws Exception;

}
