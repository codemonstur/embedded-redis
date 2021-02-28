package redis.embedded.error;

public class OsArchitectureNotFound extends RuntimeException {
    public OsArchitectureNotFound(final Throwable cause) {
        super(cause);
    }
}
