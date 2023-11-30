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

    String ENVIRONMENT_EXECUTABLE_LOCATION = "EMBEDDED_REDIS_EXECUTABLE";
    String PROPERTY_EXECUTABLE_LOCATION = "embedded.redis.executable";

    File get() throws IOException;

    static ExecutableProvider newEmbeddedRedisProvider() {
        final Map<OsArchitecture, String> executables = newProvidedVersionsMap();
        return () -> writeResourceToExecutableFile(executables.get(detectOSandArchitecture()));
    }

    static ExecutableProvider newFileThenJarResourceProvider(final Map<OsArchitecture, String> executables) {
        return () -> {
            final String executablePath = executables.get(detectOSandArchitecture());
            final File executable = new File(executablePath);
            return executable.isFile() ? executable : writeResourceToExecutableFile(executablePath);
        };
    }

    static ExecutableProvider newEnvironmentVariableProvider() {
        return newEnvironmentVariableProvider(ENVIRONMENT_EXECUTABLE_LOCATION);
    }
    static ExecutableProvider newEnvironmentVariableProvider(final String envName) {
        return () -> new File(System.getenv(envName));
    }

    static ExecutableProvider newSystemPropertyProvider() {
        return newSystemPropertyProvider(PROPERTY_EXECUTABLE_LOCATION);
    }
    static ExecutableProvider newSystemPropertyProvider(final String propertyName) {
        return () -> new File(System.getProperty(propertyName));
    }

    static ExecutableProvider newJarResourceProvider(final Map<OsArchitecture, String> executables) {
        return () -> writeResourceToExecutableFile(executables.get(detectOSandArchitecture()));
    }

    static ExecutableProvider newExecutableInPath(final String executableName) throws FileNotFoundException {
        return findBinaryInPath(executableName)::toFile;
    }

    static Map<OsArchitecture, String> newProvidedVersionsMap() {
        final Map<OsArchitecture, String> map = new HashMap<>();
        map.put(UNIX_x86, "/redis-server-6.2.7-linux-386");
        map.put(UNIX_x86_64, "/redis-server-6.2.6-v5-linux-amd64");
        map.put(UNIX_AARCH64, "/redis-server-6.2.7-linux-arm64");
        map.put(WINDOWS_x86_64, "/redis-server-5.0.14.1-windows-amd64.exe");
        map.put(MAC_OS_X_x86_64, "/redis-server-6.2.6-v5-darwin-amd64");
        map.put(MAC_OS_X_ARM64, "/redis-server-7.2-darwin-arm64");
        return map;
    }

}
