package redis.embedded.error;

/**
 * The {@code OsArchitectureNotFound} class.
 */
public class OsArchitectureNotFound extends RuntimeException {

    /**
     * Creates a new instance.
     *
     * @param cause the cause
     */
    public OsArchitectureNotFound(final Throwable cause) {
        super(cause);
    }

}
