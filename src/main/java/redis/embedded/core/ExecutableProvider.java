package redis.embedded.core;

import redis.embedded.model.OsArchitecture;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static redis.embedded.model.OsArchitecture.*;
import static redis.embedded.util.IO.writeResourceToExecutableFile;

public interface ExecutableProvider {

    String getExecutableFor(OsArchitecture osa);

    default File get() throws IOException {
        final String executablePath = getExecutableFor(detect());
        final File executable = new File(executablePath);
        return executable.isFile() ? executable : writeResourceToExecutableFile(executablePath);
    }

    static ExecutableProvider newRedis2_8_19Provider() {
        return newRedis2_8_19Map()::get;
    }

    static Map<OsArchitecture, String> newRedis2_8_19Map() {
        final Map<OsArchitecture, String> map = new HashMap<>();
        map.put(WINDOWS_x86, "redis-server-2.8.19.exe");
        map.put(WINDOWS_x86_64, "redis-server-2.8.19.exe");
        map.put(UNIX_x86, "redis-server-2.8.19-32");
        map.put(UNIX_x86_64, "redis-server-2.8.19");
        map.put(MAC_OS_X_x86, "redis-server-2.8.19.app");
        map.put(MAC_OS_X_x86_64, "redis-server-2.8.19.app");
        return map;
    }

}
