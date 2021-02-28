package redis.embedded.util;

import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.addAll;

public enum Collections {;

    public static Set<String> newHashSet(final String... items) {
        final HashSet<String> set = new HashSet<>(items.length);
        addAll(set, items);
        return set;
    }

}
