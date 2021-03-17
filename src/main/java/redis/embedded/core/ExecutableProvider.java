package redis.embedded.core;

import redis.embedded.model.OsArchitecture;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static redis.embedded.model.OsArchitecture.*;
import static redis.embedded.util.IO.findBinaryInPath;
import static redis.embedded.util.IO.writeResourceToExecutableFile;

public interface ExecutableProvider {

    File get() throws IOException;

    static ExecutableProvider newEmbeddedRedis2_8_19Provider() {
        final Map<OsArchitecture, String> executables = newRedis2_8_19Map();
        return () -> writeResourceToExecutableFile(executables.get(detectOSandArchitecture()));
    }

    static ExecutableProvider newFileThenJarResourceProvider(final Map<OsArchitecture, String> executables) {
        return () -> {
            final String executablePath = executables.get(detectOSandArchitecture());
            final File executable = new File(executablePath);
            return executable.isFile() ? executable : writeResourceToExecutableFile(executablePath);
        };
    }

    static ExecutableProvider newJarResourceProvider(final Map<OsArchitecture, String> executables) {
        return () -> writeResourceToExecutableFile(executables.get(detectOSandArchitecture()));
    }

    static ExecutableProvider newExecutableInPath(final String executableName) throws FileNotFoundException {
        return findBinaryInPath(executableName)::toFile;
    }

    static Map<OsArchitecture, String> newRedis2_8_19Map() {
        final Map<OsArchitecture, String> map = new HashMap<>();
        map.put(WINDOWS_x86, "/redis-server-2.8.19.exe");
        map.put(WINDOWS_x86_64, "/redis-server-2.8.19.exe");
        map.put(UNIX_x86, "/redis-server-2.8.19-32");
        map.put(UNIX_x86_64, "/redis-server-2.8.19");
        map.put(UNIX_AARCH64, "/redis-server-2.8.19-linux-aarch64");
        map.put(MAC_OS_X_x86, "/redis-server-2.8.19.app");
        map.put(MAC_OS_X_x86_64, "/redis-server-2.8.19.app");
        return map;
    }

}
