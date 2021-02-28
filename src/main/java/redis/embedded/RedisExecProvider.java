package redis.embedded;

import redis.embedded.model.Architecture;
import redis.embedded.model.OS;
import redis.embedded.model.OsArchitecture;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static redis.embedded.model.OsArchitecture.*;
import static redis.embedded.model.OsArchitecture.WINDOWS_x86;
import static redis.embedded.model.OsArchitecture.WINDOWS_x86_64;
import static redis.embedded.util.IO.writeResourceToExecutableFile;

public class RedisExecProvider {
    
    private final Map<OsArchitecture, String> executables = new HashMap<>();
    
    public RedisExecProvider() {
        executables.put(WINDOWS_x86, "redis-server-2.8.19.exe");
        executables.put(WINDOWS_x86_64, "redis-server-2.8.19.exe");
        executables.put(UNIX_x86, "redis-server-2.8.19-32");
        executables.put(UNIX_x86_64, "redis-server-2.8.19");
        executables.put(MAC_OS_X_x86, "redis-server-2.8.19.app");
        executables.put(MAC_OS_X_x86_64, "redis-server-2.8.19.app");
    }

    public RedisExecProvider put(final OS os, final String executable) {
        for (final Architecture arch : Architecture.values()) {
            executables.put(new OsArchitecture(os, arch), executable);
        }
        return this;
    }

    public RedisExecProvider put(final OS os, final Architecture arch, final String executable) {
        executables.put(new OsArchitecture(os, arch), executable);
        return this;
    }
    
    public File get() throws IOException {
        final String executablePath = executables.get(detect());
        final File executable = new File(executablePath);
        return executable.isFile() ? executable : writeResourceToExecutableFile(executablePath);
    }

}
