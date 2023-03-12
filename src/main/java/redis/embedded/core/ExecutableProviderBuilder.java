package redis.embedded.core;

import redis.embedded.model.Architecture;
import redis.embedded.model.OS;
import redis.embedded.model.OsArchitecture;

import java.util.HashMap;
import java.util.Map;

import static redis.embedded.core.ExecutableProvider.newJarResourceProvider;
import static redis.embedded.core.ExecutableProvider.newProvidedVersionsMap;

public class ExecutableProviderBuilder {
    private final Map<OsArchitecture, String> map = new HashMap<>();

    public ExecutableProviderBuilder addProvidedVersions() {
        map.putAll(newProvidedVersionsMap());
        return this;
    }

    public ExecutableProviderBuilder put(final OS os, final String executable) {
        for (final Architecture arch : Architecture.values()) {
            map.put(new OsArchitecture(os, arch), executable);
        }
        return this;
    }

    public ExecutableProviderBuilder put(final OS os, final Architecture arch, final String executable) {
        map.put(new OsArchitecture(os, arch), executable);
        return this;
    }

    public ExecutableProvider build() {
        return newJarResourceProvider(map);
    }
}
