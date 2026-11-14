package redis.embedded.error;

/**
 * The {@code UnknownOSName} class.
 */
public class UnknownOSName extends RuntimeException {

    /**
     * Creates a new instance.
     *
     * @param name the name
     */
    public UnknownOSName(final String name) {
        super("Unrecognized OS: " + name);
    }

}
