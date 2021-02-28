package redis.embedded.exceptions;

public class UnknownOSName extends RuntimeException {
    public UnknownOSName(final String name) {
        super("Unrecognized OS: " + name);
    }
}
