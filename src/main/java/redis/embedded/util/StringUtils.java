package redis.embedded.util;

public final class StringUtils {

    private StringUtils() {
        // utility class
    }

    public static boolean isNotBlank(final String string) {
        return string != null && !string.trim().isEmpty();
    }
}
